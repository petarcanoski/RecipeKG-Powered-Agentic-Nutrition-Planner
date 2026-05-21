package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.DailyMealPlan;
import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.MacroSummary;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutrientCap;
import com.recipekg.planner.model.NutrientTarget;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.NutritionistSelectionState;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.PlannedMeal;
import com.recipekg.planner.model.PlanningIterationTrace;
import com.recipekg.planner.model.PlanningTrace;
import com.recipekg.planner.model.RecipeBrief;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.model.ValidationIssue;
import com.recipekg.planner.model.ValidationResult;
import com.recipekg.planner.service.NutritionistBatchSelectionService;
import com.recipekg.planner.service.ValidationBrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NutritionistAgentService {

    private static final Set<String> ALLOWED_SLOTS = Set.of("breakfast", "lunch", "dinner", "snack", "dessert");

    private final WebClient webClient;
    private final NutritionistBatchSelectionService batchSelectionService;
    private final ValidationBrainService validationBrainService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${nutritionist.batch-size:30}")
    private int batchSize;

    @Value("${nutritionist.max-selection-batches:3}")
    private int maxSelectionBatches;

    @Value("${nutritionist.shortlist-size:24}")
    private int shortlistSize;

    @Value("${nutritionist.repair-recipe-limit:30}")
    private int repairRecipeLimit;

    @Value("${nutritionist.max-repair-iterations:3}")
    private int maxRepairIterations;

    @Value("${gemini.retry-503-attempts:5}")
    private int retry503Attempts;

    @Value("${gemini.retry-base-delay-ms:10000}")
    private long retryBaseDelayMillis;

    @Value("${gemini.retry-max-delay-ms:90000}")
    private long retryMaxDelayMillis;

    public NutritionPlan generateSevenDayPlan(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            List<RecipeCandidate> recipes
    ) {
        String sessionId = UUID.randomUUID().toString();
        List<PlanningIterationTrace> traces = new ArrayList<>();
        Map<String, RecipeCandidate> recipeById = assignRecipeIds(recipes);

        if (recipeById.isEmpty()) {
            return emptyPlan(performanceManifest, sessionId, "No usable recipe candidates were available for meal planning.");
        }

        NutritionistSelectionState selectionState = runBatchSelection(
                profile,
                medicalManifest,
                performanceManifest,
                recipeById,
                traces,
                sessionId
        );

        List<RecipeCandidate> shortlisted = batchSelectionService.selectedCandidates(selectionState, recipeById);
        if (shortlisted.isEmpty()) {
            System.err.println("Nutritionist plan failed: Gemini did not produce a usable recipe shortlist.");
            return errorPlan(performanceManifest, sessionId, traces, "Nutritionist could not create a usable recipe shortlist.");
        }

        NutritionPlan currentPlan = buildFinalPlan(profile, medicalManifest, performanceManifest, recipeById, shortlisted);
        if (currentPlan == null) {
            return errorPlan(performanceManifest, sessionId, traces, "Nutritionist could not create a 7-day plan.");
        }

        ValidationResult validation = validationBrainService.validate(currentPlan, performanceManifest, medicalManifest, recipeById);
        traces.add(planTrace(traces.size() + 1, "PLAN_BUILD", shortlisted, currentPlan, validation));
        logIteration(sessionId, traces.get(traces.size() - 1));

        if (validation.passed()) {
            return withTrace(currentPlan, sessionId, "PASS", traces);
        }

        for (int iteration = 1; iteration <= maxRepairIterations; iteration++) {
            List<RecipeCandidate> unusedCandidates = batchSelectionService.unusedCandidates(
                    recipeById.values(),
                    currentPlan,
                    repairRecipeLimit
            );
            List<RecipeCandidate> repairCandidates = repairCandidatePool(recipeById, currentPlan, unusedCandidates);

            currentPlan = repairPlan(
                    profile,
                    medicalManifest,
                    performanceManifest,
                    recipeById,
                    currentPlan,
                    validation,
                    repairCandidates
            );
            if (currentPlan == null) {
                return errorPlan(performanceManifest, sessionId, traces, "Nutritionist could not repair the 7-day plan.");
            }

            validation = validationBrainService.validate(currentPlan, performanceManifest, medicalManifest, recipeById);
            traces.add(planTrace(traces.size() + 1, "REPAIR", repairCandidates, currentPlan, validation));
            logIteration(sessionId, traces.get(traces.size() - 1));

            if (validation.passed()) {
                return withTrace(currentPlan, sessionId, "PASS", traces);
            }
        }

        return withTrace(currentPlan, sessionId, "REVISE", traces);
    }

    private List<RecipeCandidate> repairCandidatePool(
            Map<String, RecipeCandidate> recipeById,
            NutritionPlan currentPlan,
            List<RecipeCandidate> unusedCandidates
    ) {
        Map<String, RecipeCandidate> candidates = new LinkedHashMap<>();

        if (currentPlan != null && currentPlan.days() != null) {
            currentPlan.days().forEach(day -> {
                if (day.meals() == null) return;
                for (PlannedMeal meal : day.meals()) {
                    RecipeCandidate recipe = recipeById.get(meal.recipeId());
                    if (recipe != null) {
                        candidates.put(meal.recipeId(), recipe);
                    }
                }
            });
        }

        if (unusedCandidates != null) {
            Map<String, String> idByUri = idByUri(recipeById);
            for (RecipeCandidate recipe : unusedCandidates) {
                String id = idByUri.get(recipe.getUri());
                if (id != null) {
                    candidates.put(id, recipe);
                }
            }
        }

        return new ArrayList<>(candidates.values());
    }

    private NutritionistSelectionState runBatchSelection(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            Map<String, RecipeCandidate> recipeById,
            List<PlanningIterationTrace> traces,
            String sessionId
    ) {
        NutritionistSelectionState state = NutritionistSelectionState.empty();
        List<List<RecipeCandidate>> batches = batchSelectionService.firstBatches(
                new ArrayList<>(recipeById.values()),
                batchSize,
                maxSelectionBatches
        );

        Map<String, String> idByUri = idByUri(recipeById);
        int iteration = 1;
        for (List<RecipeCandidate> batch : batches) {
            try {
                Set<String> allowedThisRound = allowedSelectionIds(state, batch, idByUri);
                String prompt = buildBatchSelectionPrompt(
                        profile,
                        medicalManifest,
                        performanceManifest,
                        recipeById,
                        batch,
                        state,
                        iteration
                );
                String geminiResponse=callGemini(prompt);
                NutritionistSelectionState rawState = parseSelectionState(geminiResponse);
                state = batchSelectionService.normalizeState(rawState, allowedThisRound, shortlistSize);
            } catch (Exception e) {
                System.err.println("Nutritionist batch selection failed: " + e.getMessage());
            }

            traces.add(selectionTrace(iteration, batch, state));
            logIteration(sessionId, traces.get(traces.size() - 1));
            iteration++;
        }

        return state;
    }

    private Set<String> allowedSelectionIds(
            NutritionistSelectionState state,
            List<RecipeCandidate> batch,
            Map<String, String> idByUri
    ) {
        Set<String> allowed = new LinkedHashSet<>();
        if (state != null && state.selectedRecipeIds() != null) {
            allowed.addAll(state.selectedRecipeIds());
        }
        if (batch != null) {
            for (RecipeCandidate recipe : batch) {
                String id = idByUri.get(recipe.getUri());
                if (id != null) allowed.add(id);
            }
        }
        return allowed;
    }

    private NutritionPlan buildFinalPlan(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            Map<String, RecipeCandidate> recipeById,
            List<RecipeCandidate> candidates
    ) {
        List<RecipeBrief> briefs = buildRecipeBriefs(recipeById, candidates);
        if (briefs.isEmpty()) {
            System.err.println("Nutritionist final plan failed: no shortlisted recipe briefs were available.");
            return null;
        }

        try {
            String prompt = buildFinalPlanPrompt(profile, medicalManifest, performanceManifest, briefs);
            return hydrateAndCompute(parseRawPlan(callGemini(prompt)), recipeById);
        } catch (Exception e) {
            System.err.println("Nutritionist final plan failed: " + e.getMessage());
            return null;
        }
    }

    private NutritionPlan repairPlan(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            Map<String, RecipeCandidate> recipeById,
            NutritionPlan currentPlan,
            ValidationResult validationResult,
            List<RecipeCandidate> repairCandidates
    ) {
        List<RecipeBrief> briefs = buildRecipeBriefs(recipeById, repairCandidates);
        if (briefs.isEmpty()) return currentPlan;

        try {
            String prompt = buildRepairPrompt(
                    profile,
                    medicalManifest,
                    performanceManifest,
                    briefs,
                    currentPlan,
                    validationResult
            );
            return hydrateAndCompute(parseRawPlan(callGemini(prompt)), recipeById);
        } catch (Exception e) {
            System.err.println("Nutritionist repair failed: " + e.getMessage());
            return null;
        }
    }

    private Map<String, RecipeCandidate> assignRecipeIds(List<RecipeCandidate> recipes) {
        Map<String, RecipeCandidate> byId = new LinkedHashMap<>();
        if (recipes == null) return byId;

        List<RecipeCandidate> usable = recipes.stream()
                .filter(Objects::nonNull)
                .toList();

        for (int i = 0; i < usable.size(); i++) {
            byId.put("R" + (i + 1), usable.get(i));
        }
        return byId;
    }

    private List<RecipeBrief> buildRecipeBriefs(
            Map<String, RecipeCandidate> recipeById,
            List<RecipeCandidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        Map<String, String> idByUri = idByUri(recipeById);
        return candidates.stream()
                .filter(Objects::nonNull)
                .map(recipe -> {
                    String id = idByUri.get(recipe.getUri());
                    if (id == null) return null;
                    return new RecipeBrief(
                            id,
                            safe(recipe.getLabel()),
                            keyIngredients(recipe),
                            macros(recipe),
                            safeDouble(recipe.getServings()),
                            unresolvedIngredients(recipe)
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String buildBatchSelectionPrompt(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            Map<String, RecipeCandidate> recipeById,
            List<RecipeCandidate> batch,
            NutritionistSelectionState currentState,
            int batchNumber
    ) throws Exception {
        List<RecipeBrief> batchBriefs = buildRecipeBriefs(recipeById, batch);
        List<RecipeBrief> currentShortlist = buildRecipeBriefs(
                recipeById,
                batchSelectionService.selectedCandidates(currentState, recipeById)
        );

        return """
You are a nutritionist recipe scout.

Your job is NOT to build the final 7-day plan yet.
Your job is to maintain a rolling shortlist of recipe IDs that could become a strong 7-day plan.
Do not use hidden numeric scoring. Compare recipes with nutrition judgment.
Macros are USDA-derived estimates from ingredient mappings and portion inference; use them as signals, not ground truth.
If a macro value looks implausible for the label or ingredients, discount it and mention the concern.

Selection priorities:
- Fit the user's goal, activity level, and performance targets.
- Respect medical constraints.
- Keep useful coverage for breakfast, lunch, dinner, snacks, and optional desserts.
- Prefer variety across proteins, carbs, vegetables, cuisines, textures, and meal sizes.
- Keep recipes with unresolved ingredients if they are still useful, but be cautious when unresolved ingredients seem nutritionally important.
- In later batches, replace earlier choices when a new recipe is clearly more useful. Do not preserve previous choices out of inertia.

Return strict JSON only.
Use only recipe IDs present in CURRENT_SHORTLIST or NEW_BATCH.
Keep at most %d recipes in selectedRecipeIds.

USER_PROFILE:
%s

MEDICAL_MANIFEST:
%s

PERFORMANCE_MANIFEST:
%s

BATCH_NUMBER:
%d

CURRENT_SELECTION_MEMORY:
%s

CURRENT_SHORTLIST:
%s

NEW_BATCH:
%s

OUTPUT_SCHEMA:
{
  "selectedRecipeIds": ["R1"],
  "rejectedRecipeIds": ["R2"],
  "substitutions": [
    {
      "removedRecipeId": "R3",
      "addedRecipeId": "R31",
      "reason": "short reason"
    }
  ],
  "missingNeeds": ["short notes about what the shortlist still lacks"],
  "nutritionConcerns": ["short notes about sodium, implausible macros, unresolved important ingredients, etc."],
  "planningNotes": ["short notes useful for the final plan builder"]
}
""".formatted(
                Math.max(1, shortlistSize),
                mapper.writeValueAsString(compactProfile(profile)),
                mapper.writeValueAsString(compactMedical(medicalManifest)),
                mapper.writeValueAsString(performanceManifest),
                batchNumber,
                mapper.writeValueAsString(currentState),
                mapper.writeValueAsString(currentShortlist),
                mapper.writeValueAsString(batchBriefs)
        );
    }

    private String buildFinalPlanPrompt(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            List<RecipeBrief> briefs
    ) throws Exception {
        return planningBasePrompt(profile, medicalManifest, performanceManifest, briefs) + """

TASK:
Build the final 7-day plan from the shortlisted recipes.
Variety target: use at least 18 unique recipe IDs across the week when the shortlist contains 18 or more recipes.
If the shortlist has fewer than 18 recipes, use as many distinct recipes as practical.
Use fewer than 18 unique recipes only when necessary to satisfy mandatory medical constraints.
Before returning JSON, count recipeId usage across the whole week. No recipe may appear more than 2 times.
""";
    }

    private String buildRepairPrompt(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            List<RecipeBrief> briefs,
            NutritionPlan currentPlan,
            ValidationResult validationResult
    ) throws Exception {
        String currentPlanJson = mapper.writeValueAsString(compactPlan(currentPlan));
        String validationJson = mapper.writeValueAsString(validationResult);
        String repairGuidanceJson = mapper.writeValueAsString(repairGuidance(
                currentPlan,
                validationResult,
                performanceManifest,
                medicalManifest
        ));

        return planningBasePrompt(profile, medicalManifest, performanceManifest, briefs) + """

CURRENT_PLAN:
%s

VALIDATION_ISSUES:
%s

NUMERIC_REPAIR_GUIDANCE:
%s

TASK:
Repair the current plan while changing as little as possible.
Priority order:
1. Never exceed medical nutrient caps.
2. Keep breakfast, lunch, and dinner present.
3. Hit at least the validated minimum calories and protein.
4. Minimize recipe repetition and preserve variety.

Use NUMERIC_REPAIR_GUIDANCE to repair precisely:
- For medical cap excesses, reduce the listed excess amount while keeping calories/protein adequate.
- For calorie/protein deficits, add at least the listed deficit using recipes that do not create new cap excesses.
- Keep days listed under passingDays unchanged.
- Only change passing days if a global issue, such as recipe repetition, cannot be fixed any other way.
AVAILABLE_RECIPE_CANDIDATES includes the current plan recipes plus replacement options.
Variety target: when AVAILABLE_RECIPE_CANDIDATES has 18 or more recipes, use at least 18 unique recipe IDs across the repaired week.
If AVAILABLE_RECIPE_CANDIDATES has 30 or more recipes, prefer 18 to 24 unique recipe IDs.
Do not collapse the repaired plan to a small recipe subset unless that is the only way to satisfy medical caps.
Keep non-problem meals when they still fit, but replace enough repeated meals to satisfy every validation issue.
Hard constraint: no recipeId may appear more than 2 times in the returned week. There are no exceptions.
If the current plan has 4 meals/day and repetition is hard to fix, use 3 meals/day on some days instead of repeating recipes.
Before returning JSON, count recipeId usage across the whole week and fix any count above 2.
Return the complete corrected 7-day plan, not a patch.
""".formatted(currentPlanJson, validationJson, repairGuidanceJson);
    }

    private Map<String, Object> repairGuidance(
            NutritionPlan currentPlan,
            ValidationResult validationResult,
            PerformanceManifest performanceManifest,
            MedicalManifest medicalManifest
    ) {
        Set<Integer> problemDays = validationResult == null || validationResult.issues() == null
                ? Set.of()
                : validationResult.issues().stream()
                .map(ValidationIssue::day)
                .filter(day -> day > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<Integer> passingDays = currentPlan == null || currentPlan.days() == null
                ? List.of()
                : currentPlan.days().stream()
                .map(DailyMealPlan::day)
                .filter(day -> !problemDays.contains(day))
                .toList();

        List<Map<String, Object>> dayGuidance = currentPlan == null || currentPlan.days() == null
                ? List.of()
                : currentPlan.days().stream()
                .filter(day -> problemDays.contains(day.day()))
                .map(day -> dayRepairGuidance(day, validationResult, performanceManifest, medicalManifest))
                .toList();

        List<ValidationIssue> globalIssues = validationResult == null || validationResult.issues() == null
                ? List.of()
                : validationResult.issues().stream()
                .filter(issue -> issue.day() <= 0)
                .toList();

        return Map.of(
                "passingDays", passingDays,
                "problemDays", dayGuidance,
                "globalIssues", globalIssues
        );
    }

    private Map<String, Object> dayRepairGuidance(
            DailyMealPlan day,
            ValidationResult validationResult,
            PerformanceManifest performanceManifest,
            MedicalManifest medicalManifest
    ) {
        MacroSummary totals = day.estimatedTotals() == null ? MacroSummary.zero() : day.estimatedTotals();
        List<ValidationIssue> dayIssues = validationResult == null || validationResult.issues() == null
                ? List.of()
                : validationResult.issues().stream()
                .filter(issue -> issue.day() == day.day())
                .toList();

        return Map.of(
                "day", day.day(),
                "currentTotals", totals,
                "validationIssues", dayIssues,
                "performanceDeficits", performanceDeficits(totals, performanceManifest),
                "medicalCapExcesses", medicalCapExcesses(totals, medicalManifest)
        );
    }

    private List<Map<String, Object>> performanceDeficits(
            MacroSummary totals,
            PerformanceManifest performanceManifest
    ) {
        if (performanceManifest == null || performanceManifest.dailyTargets() == null) {
            return List.of();
        }

        List<Map<String, Object>> deficits = new ArrayList<>();
        addTargetDeficit(deficits, totals.calories(), target(performanceManifest, "calories"));
        addTargetDeficit(deficits, totals.protein(), target(performanceManifest, "protein"));
        return deficits;
    }

    private void addTargetDeficit(
            List<Map<String, Object>> deficits,
            double currentValue,
            NutrientTarget target
    ) {
        if (target == null || target.min() == null || currentValue >= target.min()) {
            return;
        }

        deficits.add(Map.of(
                "nutrient", safe(target.nutrient()),
                "current", round(currentValue),
                "minimum", round(target.min()),
                "deficit", round(target.min() - currentValue),
                "unit", target.unit() == null ? "" : target.unit()
        ));
    }

    private List<Map<String, Object>> medicalCapExcesses(
            MacroSummary totals,
            MedicalManifest medicalManifest
    ) {
        if (medicalManifest == null
                || medicalManifest.constraints() == null
                || medicalManifest.constraints().nutrientCaps() == null) {
            return List.of();
        }

        List<Map<String, Object>> excesses = new ArrayList<>();
        for (NutrientCap cap : medicalManifest.constraints().nutrientCaps()) {
            double currentValue = macroByName(totals, cap.nutrient());
            if (currentValue > cap.maxValue()) {
                excesses.add(Map.of(
                        "nutrient", safe(cap.nutrient()),
                        "current", round(currentValue),
                        "maximum", round(cap.maxValue()),
                        "excess", round(currentValue - cap.maxValue()),
                        "unit", cap.unit() == null ? "" : cap.unit()
                ));
            }
        }
        return excesses;
    }

    private NutrientTarget target(PerformanceManifest manifest, String nutrient) {
        if (manifest == null || manifest.dailyTargets() == null) {
            return null;
        }

        return manifest.dailyTargets().stream()
                .filter(target -> normalizeText(target.nutrient()).contains(nutrient))
                .findFirst()
                .orElse(null);
    }

    private double macroByName(MacroSummary macros, String nutrientName) {
        String name = normalizeText(nutrientName);
        if (name.contains("calories") || name.contains("energy")) return macros.calories();
        if (name.contains("protein")) return macros.protein();
        if (name.contains("carb")) return macros.carbs();
        if (name.contains("fat") || name.contains("lipid")) return macros.fat();
        if (name.contains("sugar")) return macros.sugar();
        if (name.contains("sodium")) return macros.sodium();
        return 0.0;
    }

    private String planningBasePrompt(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            List<RecipeBrief> briefs
    ) throws Exception {
        return """
You are a certified sports nutritionist and meal-planning agent.

Build a practical 7-day meal plan using ONLY the provided recipe candidates.
The user has already passed medical safety filtering. Medical constraints, if present, remain mandatory.
Performance targets guide the plan.
Recipe macros are estimates from USDA mappings and portion inference; do not treat them as perfect ground truth.
Use nutrition judgment when a macro value appears implausible for the recipe label or ingredients.

Return strict JSON only.
Do not invent recipes.
Do not invent ingredients.
Use only recipe IDs from AVAILABLE_RECIPE_CANDIDATES.

USER_PROFILE:
%s

MEDICAL_MANIFEST:
%s

PERFORMANCE_MANIFEST:
%s

AVAILABLE_RECIPE_CANDIDATES:
%s


RULES:
1. Build exactly 7 days.
2. Each day should contain 3 to 5 meals.
3. Prefer breakfast, lunch, and dinner every day.
4. Add snack or dessert only when useful.
5. Serving counts may be 0.5, 1.0, 1.5, or 2.0.
6. Hard repetition cap: no recipe ID may appear more than 2 times in the entire week. There are no exceptions.
7. Do not rank recipes by a hidden score. Choose them because they make nutritional and practical sense.
8. Be cautious with implausible macro values or recipes with important unresolved ingredients.
9. Medical constraints are mandatory.
10. Before returning JSON, count every recipeId occurrence. If any recipeId appears 3 or more times, revise before answering.
11. Output recipe IDs only, not full recipe objects.

OUTPUT_SCHEMA:
{
  "goalStatus": "string",
  "summary": "string",
  "days": [
    {
      "day": 1,
      "meals": [
        {
          "slot": "breakfast|lunch|dinner|snack|dessert",
          "recipeId": "R1",
          "servings": 1.0,
          "reason": "short practical reason"
        }
      ],
      "dayRationale": "short rationale"
    }
  ]
}
""".formatted(
                mapper.writeValueAsString(compactProfile(profile)),
                mapper.writeValueAsString(compactMedical(medicalManifest)),
                mapper.writeValueAsString(performanceManifest),
                mapper.writeValueAsString(briefs)
        );
    }

    private String callGemini(String prompt) {
        int maxAttempts = Math.max(1, retry503Attempts + 1);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGeminiOnce(prompt);
            } catch (WebClientResponseException e) {
                lastError = e;
                if (!isRetryableGeminiStatus(e) || attempt >= maxAttempts) break;

                retryAfterDelay(
                        "Gemini returned " + e.getStatusCode().value() + " " + e.getStatusText(),
                        attempt,
                        maxAttempts
                );
            } catch (WebClientRequestException e) {
                lastError = e;
                if (attempt >= maxAttempts) break;

                retryAfterDelay("Gemini request failed before receiving a response: " + rootCauseMessage(e), attempt, maxAttempts);
            }
        }

        throw lastError == null ? new RuntimeException("Gemini request failed") : lastError;
    }

    private void retryAfterDelay(String reason, int attempt, int maxAttempts) {
        long delayMillis = retryDelayMillis(attempt);
        System.err.printf(
                "%s; retrying attempt %d/%d after %dms.%n",
                reason,
                attempt + 1,
                maxAttempts,
                delayMillis
        );
        sleepBeforeRetry(delayMillis);
    }

    private boolean isRetryableGeminiStatus(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private long retryDelayMillis(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 4);
        long delay = retryBaseDelayMillis * multiplier;
        return Math.min(delay, retryMaxDelayMillis);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String callGeminiOnce(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{Map.of("text", prompt)})
                }
        );

        String raw = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node = mapper.readTree(raw);
            return node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText()
                    .trim();
        } catch (Exception e) {
            throw new RuntimeException("Nutritionist agent response parse failed", e);
        }
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry Gemini request", e);
        }
    }

    private NutritionistSelectionState parseSelectionState(String jsonText) throws Exception {
        JsonNode node = mapper.readTree(stripCodeFence(jsonText));
        if (node.isArray() && !node.isEmpty()) {
            node = node.get(0);
        }
        return mapper.treeToValue(node, NutritionistSelectionState.class);
    }

    private NutritionistRawPlan parseRawPlan(String jsonText) throws Exception {
        JsonNode node = mapper.readTree(stripCodeFence(jsonText));
        if (node.isArray() && !node.isEmpty()) {
            node = node.get(0);
        }
        return mapper.treeToValue(node, NutritionistRawPlan.class);
    }

    private NutritionPlan hydrateAndCompute(NutritionistRawPlan rawPlan, Map<String, RecipeCandidate> recipeById) {
        List<DailyMealPlan> days = rawPlan.days() == null ? List.of() : rawPlan.days().stream()
                .map(day -> hydrateDay(day, recipeById))
                .filter(day -> !day.meals().isEmpty())
                .toList();

        MacroSummary weeklyTotals = days.stream()
                .map(DailyMealPlan::estimatedTotals)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        return new NutritionPlan(
                safe(rawPlan.goalStatus()),
                safe(rawPlan.summary()),
                days,
                weeklyTotals,
                null
        );
    }

    private DailyMealPlan hydrateDay(RawDailyMealPlan rawDay, Map<String, RecipeCandidate> recipeById) {
        List<PlannedMeal> meals = rawDay.meals() == null ? List.of() : rawDay.meals().stream()
                .map(rawMeal -> hydrateMeal(rawMeal, recipeById))
                .filter(Objects::nonNull)
                .toList();

        MacroSummary totals = meals.stream()
                .map(PlannedMeal::estimatedMacros)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        return new DailyMealPlan(
                rawDay.day(),
                meals,
                totals,
                safe(rawDay.dayRationale())
        );
    }

    private PlannedMeal hydrateMeal(RawPlannedMeal rawMeal, Map<String, RecipeCandidate> recipeById) {
        if (rawMeal == null || rawMeal.recipeId() == null) return null;

        RecipeCandidate recipe = recipeById.get(rawMeal.recipeId());
        if (recipe == null) return null;

        double servingCount = normalizeServingCount(rawMeal.servings());
        return new PlannedMeal(
                normalizeSlot(rawMeal.slot()),
                rawMeal.recipeId(),
                recipe.getUri(),
                recipe.getLabel(),
                servingCount,
                macros(recipe).times(servingCount),
                safe(rawMeal.reason())
        );
    }

    private NutritionPlan emptyPlan(PerformanceManifest performanceManifest, String sessionId, String summary) {
        return new NutritionPlan(
                performanceManifest == null ? "" : safe(performanceManifest.goalStatus()),
                summary,
                List.of(),
                MacroSummary.zero(),
                new PlanningTrace(sessionId, "NO_CANDIDATES", List.of())
        );
    }

    private NutritionPlan errorPlan(
            PerformanceManifest performanceManifest,
            String sessionId,
            List<PlanningIterationTrace> traces,
            String summary
    ) {
        return new NutritionPlan(
                performanceManifest == null ? "" : safe(performanceManifest.goalStatus()),
                summary,
                List.of(),
                MacroSummary.zero(),
                new PlanningTrace(sessionId, "ERROR", traces == null ? List.of() : traces)
        );
    }

    private NutritionPlan withTrace(
            NutritionPlan plan,
            String sessionId,
            String finalStatus,
            List<PlanningIterationTrace> traces
    ) {
        return new NutritionPlan(
                plan.goalStatus(),
                plan.summary(),
                plan.days(),
                plan.weeklyTotals(),
                new PlanningTrace(sessionId, finalStatus, traces)
        );
    }

    private PlanningIterationTrace selectionTrace(
            int iterationNumber,
            List<RecipeCandidate> candidates,
            NutritionistSelectionState state
    ) {
        return new PlanningIterationTrace(
                iterationNumber,
                "BATCH_SELECTION",
                candidateUris(candidates),
                state.selectedRecipeIds(),
                new ValidationResult("SELECTED", List.of()),
                "SELECTED"
        );
    }

    private PlanningIterationTrace planTrace(
            int iterationNumber,
            String phase,
            List<RecipeCandidate> candidates,
            NutritionPlan plan,
            ValidationResult validation
    ) {
        return new PlanningIterationTrace(
                iterationNumber,
                phase,
                candidateUris(candidates),
                selectedIds(plan),
                validation,
                validation.passed() ? "PASS" : "REVISE"
        );
    }

    private void logIteration(String sessionId, PlanningIterationTrace trace) {
        int issueCount = trace.validationResult() == null || trace.validationResult().issues() == null
                ? 0
                : trace.validationResult().issues().size();
        System.out.printf(
                "Nutritionist session=%s iteration=%d phase=%s status=%s recipesSent=%d selected=%d issues=%d%n",
                sessionId,
                trace.iterationNumber(),
                trace.phase(),
                trace.status(),
                trace.recipeIdsSent() == null ? 0 : trace.recipeIdsSent().size(),
                trace.selectedRecipeIds() == null ? 0 : trace.selectedRecipeIds().size(),
                issueCount
        );
    }

    private List<String> candidateUris(List<RecipeCandidate> candidates) {
        if (candidates == null) return List.of();
        return candidates.stream()
                .map(RecipeCandidate::getUri)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> selectedIds(NutritionPlan plan) {
        if (plan == null || plan.days() == null) return List.of();
        return plan.days().stream()
                .flatMap(day -> day.meals().stream())
                .map(PlannedMeal::recipeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> compactPlan(NutritionPlan plan) {
        if (plan == null || plan.days() == null) return List.of();
        return plan.days().stream()
                .map(day -> {
                    Map<String, Object> dayMap = new LinkedHashMap<>();
                    dayMap.put("day", day.day());
                    dayMap.put("meals", day.meals().stream().map(meal -> {
                        Map<String, Object> mealMap = new LinkedHashMap<>();
                        mealMap.put("slot", meal.slot());
                        mealMap.put("recipeId", meal.recipeId());
                        mealMap.put("servings", meal.servings());
                        return mealMap;
                    }).toList());
                    dayMap.put("estimatedTotals", day.estimatedTotals());
                    return dayMap;
                })
                .toList();
    }

    private Map<String, String> idByUri(Map<String, RecipeCandidate> recipeById) {
        Map<String, String> idByUri = new LinkedHashMap<>();
        recipeById.forEach((id, recipe) -> idByUri.put(recipe.getUri(), id));
        return idByUri;
    }

    private List<String> unresolvedIngredients(RecipeCandidate recipe) {
        if (recipe.getIngredients() == null) return List.of();
        return recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isMacroResolved())
                .map(IngredientUse::getName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .limit(6)
                .toList();
    }

    private List<String> keyIngredients(RecipeCandidate recipe) {
        if (recipe.getIngredients() == null) return List.of();
        return recipe.getIngredients().stream()
                .sorted(Comparator.comparingInt(this::ingredientDisplayPriority))
                .map(IngredientUse::getName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .limit(8)
                .toList();
    }

    private int ingredientDisplayPriority(IngredientUse ingredient) {
        String name = normalizeText(ingredient.getName());
        if (name.isBlank()) return 5;
        if (containsAnyText(name, "water")) return 4;
        if (containsAnyText(name, "salt", "pepper", "spice", "seasoning", "cilantro", "parsley", "thyme", "oregano", "basil")) {
            return 3;
        }
        if (containsAnyText(name, "steak", "beef", "chicken", "turkey", "fish", "salmon", "tuna", "pork", "egg", "tofu", "bean", "lentil")) {
            return 0;
        }
        if (containsAnyText(name, "oil", "butter", "cream", "cheese", "nuts", "peanut", "rice", "pasta", "bread", "tortilla", "potato")) {
            return 1;
        }
        return 2;
    }

    private MacroSummary macros(RecipeCandidate recipe) {
        return new MacroSummary(
                round(recipe.getCalories()),
                round(recipe.getProtein()),
                round(recipe.getCarbs()),
                round(recipe.getFat()),
                round(recipe.getSugar()),
                round(recipe.getSodium())
        );
    }

    private Map<String, Object> compactProfile(UserProfile profile) {
        if (profile == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("age", profile.getAge());
        map.put("gender", profile.getGender());
        map.put("heightCm", profile.getHeight());
        map.put("weightKg", profile.getWeight());
        map.put("activityLevel", profile.getActivityLevel());
        map.put("goal", profile.getGoal());
        map.put("allergies", profile.getAllergies());
        map.put("diseases", profile.getDiseases());
        return map;
    }

    private Map<String, Object> compactMedical(MedicalManifest manifest) {
        if (manifest == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", manifest.status());
        map.put("constraints", manifest.constraints());
        return map;
    }

    private String stripCodeFence(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").trim();
            trimmed = trimmed.replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private String normalizeSlot(String slot) {
        String normalized = normalizeText(slot);
        return ALLOWED_SLOTS.contains(normalized) ? normalized : "snack";
    }

    private double normalizeServingCount(double servings) {
        if (servings <= 0) return 1.0;
        double rounded = Math.round(servings * 2.0) / 2.0;
        return clamp(rounded, 0.5, 2.0);
    }

    private boolean containsAnyText(String text, String... needles) {
        if (text == null || text.isBlank()) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record NutritionistRawPlan(
            String goalStatus,
            String summary,
            List<RawDailyMealPlan> days
    ) {}

    private record RawDailyMealPlan(
            int day,
            List<RawPlannedMeal> meals,
            String dayRationale
    ) {}

    private record RawPlannedMeal(
            String slot,
            String recipeId,
            double servings,
            String reason
    ) {}
}
