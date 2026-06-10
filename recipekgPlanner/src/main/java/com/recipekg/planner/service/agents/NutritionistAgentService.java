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

    @Value("${gemini.nutritionist-model:gemini-3-flash-preview}")
    private String nutritionistModel;

    @Value("${gemini.nutritionist-selection-model:gemini-2.5-flash-lite}")
    private String nutritionistSelectionModel;

    @Value("${gemini.nutritionist-repair-model:gemini-2.5-flash-lite}")
    private String nutritionistRepairModel;

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

        PlanAttempt finalAttempt = buildFinalPlan(profile, medicalManifest, performanceManifest, recipeById, shortlisted);
        NutritionPlan currentPlan = finalAttempt.plan();
        if (currentPlan == null) {
            traces.add(planTrace(
                    traces.size() + 1,
                    "PLAN_BUILD",
                    shortlisted,
                    null,
                    new ValidationResult("ERROR", List.of()),
                    finalAttempt.promptText(),
                    finalAttempt.responseText(),
                    finalAttempt.errorMessage(),
                    finalAttempt.model()
            ));
            logIteration(sessionId, traces.get(traces.size() - 1));
            return errorPlan(performanceManifest, sessionId, traces, "Nutritionist could not create a 7-day plan.");
        }

        ValidationResult validation = validationBrainService.validate(currentPlan, performanceManifest, medicalManifest, recipeById);
        traces.add(planTrace(
                traces.size() + 1,
                "PLAN_BUILD",
                shortlisted,
                currentPlan,
                validation,
                finalAttempt.promptText(),
                finalAttempt.responseText(),
                finalAttempt.errorMessage(),
                finalAttempt.model()
        ));
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

            PlanAttempt repairAttempt = repairPlan(
                    profile,
                    medicalManifest,
                    performanceManifest,
                    recipeById,
                    currentPlan,
                    validation,
                    repairCandidates
            );
            currentPlan = repairAttempt.plan();
            if (currentPlan == null) {
                traces.add(planTrace(
                        traces.size() + 1,
                        "REPAIR",
                        repairCandidates,
                        null,
                        new ValidationResult("ERROR", List.of()),
                        repairAttempt.promptText(),
                        repairAttempt.responseText(),
                        repairAttempt.errorMessage(),
                        repairAttempt.model()
                ));
                logIteration(sessionId, traces.get(traces.size() - 1));
                return errorPlan(performanceManifest, sessionId, traces, "Nutritionist could not repair the 7-day plan.");
            }

            validation = validationBrainService.validate(currentPlan, performanceManifest, medicalManifest, recipeById);
            traces.add(planTrace(
                    traces.size() + 1,
                    "REPAIR",
                    repairCandidates,
                    currentPlan,
                    validation,
                    repairAttempt.promptText(),
                    repairAttempt.responseText(),
                    repairAttempt.errorMessage(),
                    repairAttempt.model()
            ));
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
            String prompt = null;
            String geminiResponse = null;
            String errorMessage = null;
            try {
                Set<String> allowedThisRound = allowedSelectionIds(state, batch, idByUri);
                prompt = buildBatchSelectionPrompt(
                        profile,
                        medicalManifest,
                        performanceManifest,
                        recipeById,
                        batch,
                        state,
                        iteration
                );
                logPromptSize("BATCH_SELECTION", iteration, prompt);
                geminiResponse=callGemini(prompt, nutritionistSelectionModel);
                NutritionistSelectionState rawState = parseSelectionState(geminiResponse);
                state = batchSelectionService.normalizeState(rawState, allowedThisRound, shortlistSize);
            } catch (Exception e) {
                errorMessage = rootCauseMessage(e);
                System.err.println("Nutritionist batch selection failed: " + e.getMessage());
            }

            traces.add(selectionTrace(iteration, batch, state, prompt, geminiResponse, errorMessage, nutritionistSelectionModel));
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

    private PlanAttempt buildFinalPlan(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            Map<String, RecipeCandidate> recipeById,
            List<RecipeCandidate> candidates
    ) {
        List<RecipeBrief> briefs = buildRecipeBriefs(recipeById, candidates);
        if (briefs.isEmpty()) {
            System.err.println("Nutritionist final plan failed: no shortlisted recipe briefs were available.");
            return new PlanAttempt(null, null, null, "No shortlisted recipe briefs were available.", nutritionistModel);
        }

        String prompt = null;
        String geminiResponse = null;
        try {
            prompt = buildFinalPlanPrompt(profile, medicalManifest, performanceManifest, briefs);
            logPromptSize("PLAN_BUILD", 0, prompt);
            geminiResponse = callGemini(prompt, nutritionistModel);
            NutritionPlan plan = hydrateAndCompute(parseRawPlan(geminiResponse), recipeById);
            return new PlanAttempt(plan, prompt, geminiResponse, null, nutritionistModel);
        } catch (Exception e) {
            System.err.println("Nutritionist final plan failed: " + e.getMessage());
            return new PlanAttempt(null, prompt, geminiResponse, rootCauseMessage(e), nutritionistModel);
        }
    }

    private PlanAttempt repairPlan(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            Map<String, RecipeCandidate> recipeById,
            NutritionPlan currentPlan,
            ValidationResult validationResult,
            List<RecipeCandidate> repairCandidates
    ) {
        List<RecipeBrief> briefs = buildRecipeBriefs(recipeById, repairCandidates);
        if (briefs.isEmpty()) {
            return new PlanAttempt(currentPlan, null, null, "No repair recipe briefs were available.", nutritionistRepairModel);
        }

        String prompt = null;
        String geminiResponse = null;
        try {
            prompt = buildRepairPrompt(
                    profile,
                    medicalManifest,
                    performanceManifest,
                    briefs,
                    currentPlan,
                    validationResult
            );
            logPromptSize("REPAIR", 0, prompt);
            geminiResponse = callGemini(prompt, nutritionistRepairModel);
            NutritionPlan plan = hydrateAndCompute(parseRawPlan(geminiResponse), recipeById);
            return new PlanAttempt(plan, prompt, geminiResponse, null, nutritionistRepairModel);
        } catch (Exception e) {
            System.err.println("Nutritionist repair failed: " + e.getMessage());
            return new PlanAttempt(null, prompt, geminiResponse, rootCauseMessage(e), nutritionistRepairModel);
        }
    }

    private record PlanAttempt(
            NutritionPlan plan,
            String promptText,
            String responseText,
            String errorMessage,
            String model
    ) {}

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

    private List<Map<String, Object>> buildCompactRecipeBriefs(
            Map<String, RecipeCandidate> recipeById,
            List<RecipeCandidate> candidates,
            int ingredientLimit,
            int unresolvedLimit
    ) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        Map<String, String> idByUri = idByUri(recipeById);
        return candidates.stream()
                .filter(Objects::nonNull)
                .map(recipe -> compactRecipeBrief(idByUri.get(recipe.getUri()), recipe, ingredientLimit, unresolvedLimit))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> compactRecipeBrief(
            String id,
            RecipeCandidate recipe,
            int ingredientLimit,
            int unresolvedLimit
    ) {
        if (id == null || recipe == null) return null;

        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", id);
        brief.put("label", safe(recipe.getLabel()));
        brief.put("macros", macros(recipe));
        brief.put("servings", safeDouble(recipe.getServings()));
        brief.put("ingredients", keyIngredients(recipe).stream()
                .limit(Math.max(0, ingredientLimit))
                .toList());

        List<String> unresolved = unresolvedIngredients(recipe).stream()
                .limit(Math.max(0, unresolvedLimit))
                .toList();
        if (!unresolved.isEmpty()) {
            brief.put("unresolved", unresolved);
        }

        return brief;
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
        List<Map<String, Object>> batchBriefs = buildCompactRecipeBriefs(recipeById, batch, 5, 3);
        List<Map<String, Object>> currentShortlist = buildCompactRecipeBriefs(
                recipeById,
                batchSelectionService.selectedCandidates(currentState, recipeById),
                4,
                2
        );

        return """
You are a nutritionist recipe scout. Maintain a rolling shortlist; do not build the 7-day plan yet.
Rules: use only IDs in CURRENT_SHORTLIST or NEW_BATCH; max %d selected IDs; respect medical caps/exclusions; prefer goal fit, variety, usable macros, and meal-slot coverage. Keep 5-7 breakfast-suitable recipes when available. Judge breakfast suitability from label, ingredients, preparation style, and practical context. Do not choose breakfast coverage only by macro density. USDA macros are estimates; note implausible values or important unresolved ingredients.

USER_PROFILE:
%s

MEDICAL:
%s

TARGETS:
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
  "missingNeeds": ["short notes about what the shortlist still lacks, including breakfast gaps"],
  "nutritionConcerns": ["short notes about sodium, implausible macros, unresolved important ingredients, etc."],
  "planningNotes": ["short notes useful for the final plan builder, including which selected recipes seem most breakfast-suitable"]
}
""".formatted(
                Math.max(1, shortlistSize),
                mapper.writeValueAsString(compactProfile(profile)),
                mapper.writeValueAsString(compactMedical(medicalManifest)),
                mapper.writeValueAsString(compactPerformance(performanceManifest)),
                batchNumber,
                mapper.writeValueAsString(compactSelectionState(currentState)),
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
        return planningBasePrompt(profile, medicalManifest, performanceManifest, briefs, 8, 4) + """

TASK:
Build the final 7-day plan. Use at least 18 unique recipe IDs when 18+ are available, otherwise use as many distinct IDs as practical. Use fewer only for medical constraints. Count recipe usage before answering; max 2 uses per recipe/week.
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
        String currentPlanJson = mapper.writeValueAsString(compactPlanForRepair(currentPlan, validationResult));
        String validationJson = mapper.writeValueAsString(compactValidation(validationResult));
        String repairGuidanceJson = mapper.writeValueAsString(repairGuidance(
                currentPlan,
                validationResult,
                performanceManifest,
                medicalManifest
        ));

        return planningBasePrompt(profile, medicalManifest, performanceManifest, briefs, 5, 3) + """

CURRENT_PLAN:
%s

VALIDATION_ISSUES:
%s

NUMERIC_REPAIR_GUIDANCE:
%s

TASK:
Repair the plan with minimal changes. Priority: medical caps, breakfast/lunch/dinner present, breakfast appropriateness by your judgment, calorie/protein minimums, repetition/variety. Use NUMERIC_REPAIR_GUIDANCE exactly. Keep passing days unchanged unless needed for global repetition. Max 2 uses per recipe/week. Prefer 18-24 unique IDs when enough candidates exist. Return the complete corrected 7-day plan, not a patch.
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
            List<RecipeBrief> briefs,
            int ingredientLimit,
            int unresolvedLimit
    ) throws Exception {
        return """
You are a certified sports nutritionist. Build a practical 7-day plan using ONLY candidate IDs. JSON only. Do not invent recipes/ingredients. USDA macros are estimates; use nutrition judgment for implausible values.

USER_PROFILE:
%s

MEDICAL:
%s

TARGETS:
%s

AVAILABLE_RECIPE_CANDIDATES:
%s


RULES:
1. Exactly 7 days; 3-5 meals/day; include breakfast/lunch/dinner daily.
2. Breakfast: choose recipes you judge breakfast-suitable from label, ingredients, preparation style, and context when available. Do not choose breakfast solely for macro density.
3. Medical constraints are mandatory.
4. Servings allowed: 0.5, 1.0, 1.5, 2.0.
5. Max 2 uses per recipe ID/week; count before answering.
6. Add snack/dessert only when useful.
7. Output recipe IDs only, not full recipe objects.

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
                mapper.writeValueAsString(compactPerformance(performanceManifest)),
                mapper.writeValueAsString(compactRecipeBriefs(briefs, ingredientLimit, unresolvedLimit))
        );
    }

    private String callGemini(String prompt) {
        return callGemini(prompt, nutritionistModel);
    }

    private String callGemini(String prompt, String model) {
        int maxAttempts = Math.max(1, retry503Attempts + 1);
        RuntimeException lastError = null;
        String modelId = safeModel(model);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGeminiOnce(prompt, modelId);
            } catch (WebClientResponseException e) {
                lastError = e;
                if (!isRetryableGeminiStatus(e) || attempt >= maxAttempts) break;

                retryAfterDelay(
                        "Gemini model " + modelId + " returned " + e.getStatusCode().value() + " " + e.getStatusText(),
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

    private String callGeminiOnce(String prompt, String model) {
        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{Map.of("text", prompt)})
                },
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        String raw = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey)
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
        JsonNode node = mapper.readTree(extractJsonPayload(jsonText));
        if (node.isArray() && !node.isEmpty()) {
            node = node.get(0);
        }
        return mapper.treeToValue(node, NutritionistSelectionState.class);
    }

    private NutritionistRawPlan parseRawPlan(String jsonText) throws Exception {
        JsonNode node = mapper.readTree(extractJsonPayload(jsonText));
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
            NutritionistSelectionState state,
            String promptText,
            String responseText,
            String errorMessage,
            String model
    ) {
        boolean failed = errorMessage != null && !errorMessage.isBlank();
        return new PlanningIterationTrace(
                iterationNumber,
                "BATCH_SELECTION",
                candidateUris(candidates),
                state.selectedRecipeIds(),
                new ValidationResult(failed ? "ERROR" : "SELECTED", List.of()),
                failed ? "ERROR" : "SELECTED",
                promptText,
                responseText,
                errorMessage,
                model
        );
    }

    private PlanningIterationTrace planTrace(
            int iterationNumber,
            String phase,
            List<RecipeCandidate> candidates,
            NutritionPlan plan,
            ValidationResult validation,
            String promptText,
            String responseText,
            String errorMessage,
            String model
    ) {
        boolean failed = errorMessage != null && !errorMessage.isBlank();
        ValidationResult safeValidation = validation == null
                ? new ValidationResult(failed ? "ERROR" : "REVISE", List.of())
                : validation;
        return new PlanningIterationTrace(
                iterationNumber,
                phase,
                candidateUris(candidates),
                selectedIds(plan),
                safeValidation,
                failed ? "ERROR" : safeValidation.passed() ? "PASS" : "REVISE",
                promptText,
                responseText,
                errorMessage,
                model
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

    private List<Map<String, Object>> compactPlanForRepair(NutritionPlan plan, ValidationResult validationResult) {
        if (plan == null || plan.days() == null) return List.of();

        Set<Integer> problemDays = validationResult == null || validationResult.issues() == null
                ? Set.of()
                : validationResult.issues().stream()
                .map(ValidationIssue::day)
                .filter(day -> day > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        boolean hasGlobalIssues = validationResult != null
                && validationResult.issues() != null
                && validationResult.issues().stream().anyMatch(issue -> issue.day() <= 0);

        return plan.days().stream()
                .filter(day -> hasGlobalIssues || problemDays.contains(day.day()))
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
                    dayMap.put("totals", day.estimatedTotals());
                    return dayMap;
                })
                .toList();
    }

    private Map<String, Object> compactValidation(ValidationResult validationResult) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (validationResult == null) {
            map.put("status", "");
            map.put("issues", List.of());
            return map;
        }

        map.put("status", validationResult.status());
        map.put("issues", validationResult.issues() == null ? List.of() : validationResult.issues().stream()
                .map(issue -> {
                    Map<String, Object> issueMap = new LinkedHashMap<>();
                    issueMap.put("type", issue.type());
                    issueMap.put("day", issue.day());
                    issueMap.put("severity", issue.severity());
                    issueMap.put("message", issue.message());
                    return issueMap;
                })
                .toList());
        return map;
    }

    private List<Map<String, Object>> compactRecipeBriefs(List<RecipeBrief> briefs, int ingredientLimit, int unresolvedLimit) {
        if (briefs == null || briefs.isEmpty()) return List.of();

        return briefs.stream()
                .filter(Objects::nonNull)
                .map(brief -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", brief.id());
                    map.put("label", brief.label());
                    map.put("macros", brief.macrosPerServing());
                    map.put("servings", brief.servings());
                    map.put("ingredients", brief.ingredients() == null ? List.of() : brief.ingredients().stream()
                            .limit(Math.max(0, ingredientLimit))
                            .toList());

                    List<String> unresolved = brief.unresolvedIngredients() == null ? List.of() : brief.unresolvedIngredients().stream()
                            .limit(Math.max(0, unresolvedLimit))
                            .toList();
                    if (!unresolved.isEmpty()) {
                        map.put("unresolved", unresolved);
                    }
                    return map;
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
        if (manifest.constraints() != null) {
            map.put("exclude", manifest.constraints().hardExclusions() == null ? List.of() : manifest.constraints().hardExclusions());
            map.put("caps", manifest.constraints().nutrientCaps() == null ? List.of() : manifest.constraints().nutrientCaps().stream()
                    .map(cap -> Map.of(
                            "nutrient", safe(cap.nutrient()),
                            "max", round(cap.maxValue()),
                            "unit", safe(cap.unit())
                    ))
                    .toList());
            map.put("boosts", manifest.constraints().requiredBoosts() == null ? List.of() : manifest.constraints().requiredBoosts());
        }
        return map;
    }

    private Map<String, Object> compactPerformance(PerformanceManifest manifest) {
        if (manifest == null) return Map.of();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("goal", manifest.goalStatus());
        map.put("daily", compactTargets(manifest.dailyTargets()));
        return map;
    }

    private Map<String, Object> compactTargets(List<NutrientTarget> targets) {
        if (targets == null || targets.isEmpty()) return Map.of();

        Map<String, Object> map = new LinkedHashMap<>();
        for (NutrientTarget target : targets) {
            if (target == null || target.nutrient() == null) continue;

            Map<String, Object> targetMap = new LinkedHashMap<>();
            if (target.min() != null) targetMap.put("min", round(target.min()));
            if (target.target() != null) targetMap.put("target", round(target.target()));
            if (target.max() != null) targetMap.put("max", round(target.max()));
            targetMap.put("unit", safe(target.unit()));
            map.put(normalizeText(target.nutrient()), targetMap);
        }
        return map;
    }

    private Map<String, Object> compactSelectionState(NutritionistSelectionState state) {
        if (state == null) return Map.of();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("selectedRecipeIds", state.selectedRecipeIds() == null ? List.of() : state.selectedRecipeIds());
        map.put("missingNeeds", lastItems(state.missingNeeds(), 4));
        map.put("nutritionConcerns", lastItems(state.nutritionConcerns(), 5));
        map.put("planningNotes", lastItems(state.planningNotes(), 4));
        return map;
    }

    private List<String> lastItems(List<String> values, int maxItems) {
        if (values == null || values.isEmpty() || maxItems <= 0) return List.of();
        int fromIndex = Math.max(0, values.size() - maxItems);
        return values.subList(fromIndex, values.size());
    }

    private void logPromptSize(String phase, int iteration, String prompt) {
        System.out.printf(
                "Nutritionist prompt phase=%s iteration=%d chars=%d%n",
                phase,
                iteration,
                prompt == null ? 0 : prompt.length()
        );
    }

    private String safeModel(String model) {
        return model == null || model.isBlank()
                ? "gemini-3-flash-preview"
                : model.trim();
    }

    private String extractJsonPayload(String text) {
        String trimmed = stripCodeFence(text);
        if (trimmed.isBlank()) return trimmed;

        int objectStart = trimmed.indexOf('{');
        int arrayStart = trimmed.indexOf('[');
        int start;

        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }

        if (start < 0) {
            return trimmed;
        }

        char opener = trimmed.charAt(start);
        char closer = opener == '[' ? ']' : '}';
        int end = trimmed.lastIndexOf(closer);
        if (end <= start) {
            return trimmed.substring(start).trim();
        }

        return trimmed.substring(start, end + 1).trim();
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
