package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.DailyMealPlan;
import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.MacroSummary;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.PlannedMeal;
import com.recipekg.planner.model.RecipeBrief;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NutritionistAgentService {

    private static final Set<String> ALLOWED_SLOTS = Set.of("breakfast", "lunch", "dinner", "snack", "dessert");

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${nutritionist.recipe-limit:60}")
    private int recipeLimit;

    public NutritionPlan generateSevenDayPlan(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            List<RecipeCandidate> recipes
    ) {
        annotateMacroQuality(recipes);
        Map<String, RecipeCandidate> recipeById = assignRecipeIds(choosePromptRecipes(recipes));
        List<RecipeBrief> briefs = buildRecipeBriefs(recipeById);

        if (briefs.isEmpty()) {
            return emptyPlan(performanceManifest, "No usable recipe candidates were available for meal planning.");
        }

        try {
            String prompt = buildPrompt(profile, medicalManifest, performanceManifest, briefs);
            String text = callGemini(prompt);
            NutritionistRawPlan rawPlan = parseRawPlan(text);
            NutritionPlan plan = hydrateAndCompute(rawPlan, recipeById);
            if (isUsable(plan)) {
                return plan;
            }
        } catch (Exception e) {
            System.err.println("Nutritionist agent failed; using deterministic fallback: " + e.getMessage());
        }
        return null;
//        return fallbackPlan(performanceManifest, recipeById);
    }

    private List<RecipeCandidate> choosePromptRecipes(List<RecipeCandidate> recipes) {
        if (recipes == null || recipes.isEmpty()) return List.of();

        return recipes.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingDouble(RecipeCandidate::getNutritionistPromptScore).reversed()
                        .thenComparing(Comparator.comparingDouble(RecipeCandidate::getPerformanceScore).reversed()))
                .limit(Math.max(10, recipeLimit))
                .toList();
    }

    private Map<String, RecipeCandidate> assignRecipeIds(List<RecipeCandidate> recipes) {
        Map<String, RecipeCandidate> byId = new LinkedHashMap<>();
        for (int i = 0; i < recipes.size(); i++) {
            byId.put("R" + (i + 1), recipes.get(i));
        }
        return byId;
    }

    private List<RecipeBrief> buildRecipeBriefs(Map<String, RecipeCandidate> recipeById) {
        return recipeById.entrySet().stream()
                .map(entry -> {
                    RecipeCandidate recipe = entry.getValue();
                    return new RecipeBrief(
                            entry.getKey(),
                            safe(recipe.getLabel()),
                            inferMealHints(recipe),
                            keyIngredients(recipe),
                            macros(recipe),
                            safeDouble(recipe.getServings()),
                            recipe.getPerformanceScore(),
                            recipe.getMacroConfidence(),
                            recipe.getNutritionistPromptScore(),
                            unresolvedIngredients(recipe)
                    );
                })
                .toList();
    }

    private String buildPrompt(
            UserProfile profile,
            MedicalManifest medicalManifest,
            PerformanceManifest performanceManifest,
            List<RecipeBrief> briefs
    ) throws Exception {
        String profileJson = mapper.writeValueAsString(compactProfile(profile));
        String medicalJson = mapper.writeValueAsString(compactMedical(medicalManifest));
        String performanceJson = mapper.writeValueAsString(performanceManifest);
        String recipeJson = mapper.writeValueAsString(briefs);

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

MEAL_SLOT_GUIDANCE:
Breakfast: choose breakfast-like, lighter, simple, early-day appropriate meals.
Lunch: choose balanced meals with protein plus carbs or vegetables.
Dinner: choose complete entree-style meals, often larger and higher protein.
Snack: choose smaller/simple meals or half servings.
Dessert: optional; use sweet recipes only when they fit the day.

RULES:
1. Build exactly 7 days.
2. Each day must contain 3 to 5 meals.
3. Prefer breakfast, lunch, and dinner every day.
4. Add snack or dessert only when useful.
5. Serving counts may be 0.5, 1.0, 1.5, or 2.0.
6. For high activity or hypertrophy, prioritize protein and enough calories.
7. For fat loss, prioritize high protein and moderate calories.
8. Avoid repeating the same recipe more than twice unless the pool is limited.
9. Prefer higher macroConfidence and nutritionistPromptScore recipes.
10. Be cautious with implausible macro values or recipes with important unresolved ingredients.
11. performanceScore is a helpful hint, not a hard rule.
12. Medical constraints are mandatory.
13. Output recipe IDs only, not full recipe objects.

OUTPUT_SCHEMA:
{
  "goalStatus": "string",
  "summary": "string",
  "days": [
    {
      "day": 1,
      "theme": "string",
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
""".formatted(profileJson, medicalJson, performanceJson, recipeJson);
    }

    private String callGemini(String prompt) {
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

    private NutritionistRawPlan parseRawPlan(String jsonText) throws Exception {
        String trimmed = stripCodeFence(jsonText);
        JsonNode node = mapper.readTree(trimmed);
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
                weeklyTotals
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
                safe(rawDay.theme()),
                meals,
                totals,
                safe(rawDay.dayRationale())
        );
    }

    private PlannedMeal hydrateMeal(RawPlannedMeal rawMeal, Map<String, RecipeCandidate> recipeById) {
        if (rawMeal == null || rawMeal.recipeId() == null) return null;

        RecipeCandidate recipe = recipeById.get(rawMeal.recipeId());
        if (recipe == null) return null;

        String slot = normalizeSlot(rawMeal.slot());
        double servingCount = normalizeServingCount(rawMeal.servings());
        MacroSummary estimated = macros(recipe).times(servingCount);

        return new PlannedMeal(
                slot,
                rawMeal.recipeId(),
                recipe.getUri(),
                recipe.getLabel(),
                servingCount,
                estimated,
                safe(rawMeal.reason())
        );
    }

    private NutritionPlan fallbackPlan(PerformanceManifest performanceManifest, Map<String, RecipeCandidate> recipeById) {
        List<String> ids = new ArrayList<>(recipeById.keySet());
        List<DailyMealPlan> days = new ArrayList<>();
        String[] slots = {"breakfast", "lunch", "dinner"};

        for (int day = 1; day <= 7 && !ids.isEmpty(); day++) {
            List<PlannedMeal> meals = new ArrayList<>();
            for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
                String recipeId = ids.get((day - 1 + slotIndex) % ids.size());
                RecipeCandidate recipe = recipeById.get(recipeId);
                MacroSummary estimated = macros(recipe);
                meals.add(new PlannedMeal(
                        slots[slotIndex],
                        recipeId,
                        recipe.getUri(),
                        recipe.getLabel(),
                        1.0,
                        estimated,
                        "Fallback selection from highest-quality available recipe candidates."
                ));
            }

            MacroSummary totals = meals.stream()
                    .map(PlannedMeal::estimatedMacros)
                    .reduce(MacroSummary.zero(), MacroSummary::plus);

            days.add(new DailyMealPlan(
                    day,
                    "Balanced fallback day",
                    meals,
                    totals,
                    "Generated without LLM planning because the nutritionist agent response was unavailable."
            ));
        }

        MacroSummary weeklyTotals = days.stream()
                .map(DailyMealPlan::estimatedTotals)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        return new NutritionPlan(
                performanceManifest == null ? "" : safe(performanceManifest.goalStatus()),
                "Fallback 7-day plan generated from high-confidence recipe candidates.",
                days,
                weeklyTotals
        );
    }

    private NutritionPlan emptyPlan(PerformanceManifest performanceManifest, String summary) {
        return new NutritionPlan(
                performanceManifest == null ? "" : safe(performanceManifest.goalStatus()),
                summary,
                List.of(),
                MacroSummary.zero()
        );
    }

    private boolean isUsable(NutritionPlan plan) {
        return plan != null && plan.days() != null && plan.days().size() == 7;
    }

    private void annotateMacroQuality(List<RecipeCandidate> recipes) {
        if (recipes == null) return;
        for (RecipeCandidate recipe : recipes) {
            if (recipe == null) continue;
            recipe.setMacroConfidence(macroConfidence(recipe));
            recipe.setNutritionistPromptScore(nutritionistPromptScore(recipe));
        }
    }

    private double nutritionistPromptScore(RecipeCandidate recipe) {
        double confidence = macroConfidence(recipe);
        double performance = clamp(recipe.getPerformanceScore(), 0.0, 1.0);
        return clamp((confidence * 0.60) + (performance * 0.40), 0.0, 1.0);
    }

    private double macroConfidence(RecipeCandidate recipe) {
        if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) return 0.0;

        double totalWeight = 0.0;
        double resolvedWeight = 0.0;

        for (IngredientUse ingredient : recipe.getIngredients()) {
            double weight = ingredientImportance(ingredient);
            totalWeight += weight;
            if (ingredient.isMacroResolved()) {
                resolvedWeight += weight;
            }
        }

        if (totalWeight <= 0) return 1.0;
        return clamp(resolvedWeight / totalWeight, 0.0, 1.0);
    }

    private double ingredientImportance(IngredientUse ingredient) {
        String name = normalizeText(ingredient.getName());
        String unit = normalizeText(ingredient.getUnit());
        double quantity = parseQuantity(ingredient.getQuantity());

        if (name.isBlank()) return 0.5;
        if (containsAnyText(name, "water")) return 0.0;
        if (containsAnyText(name, "salt", "pepper", "spice", "seasoning", "cilantro", "parsley", "thyme", "oregano", "basil")) {
            return 0.2;
        }

        double weight = 1.0;
        if (containsAnyText(unit, "lb", "lbs", "pound", "kg", "kilogram", "whole")) weight += 1.5;
        if (containsAnyText(unit, "cup", "cups", "can", "container", "package")) weight += 0.8;
        if (quantity >= 3.0) weight += 0.5;
        if (containsAnyText(name, "steak", "beef", "chicken", "turkey", "fish", "salmon", "tuna", "pork", "egg", "tofu", "bean", "lentil")) {
            weight += 1.0;
        }
        if (containsAnyText(name, "oil", "butter", "cream", "cheese", "nuts", "peanut", "rice", "pasta", "bread", "tortilla", "potato")) {
            weight += 0.6;
        }

        return Math.max(0.1, weight);
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
                .map(IngredientUse::getName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .limit(8)
                .toList();
    }

    private List<String> inferMealHints(RecipeCandidate recipe) {
        String text = normalizeText(recipe.getLabel() + " " + keyIngredients(recipe).stream().collect(Collectors.joining(" ")));
        List<String> hints = new ArrayList<>();

        if (containsAnyText(text, "egg", "oat", "pancake", "waffle", "toast", "breakfast", "yogurt", "smoothie")) {
            hints.add("breakfast");
        }
        if (containsAnyText(text, "cookie", "cake", "ice cream", "pie", "brownie", "dessert", "sweet")) {
            hints.add("dessert");
        }
        if (recipe.getCalories() < 350 || containsAnyText(text, "snack", "smoothie", "fruit")) {
            hints.add("snack");
        }
        if (containsAnyText(text, "salad", "sandwich", "wrap", "taco", "bowl", "soup")) {
            hints.add("lunch");
        }
        if (hints.isEmpty() || recipe.getCalories() >= 350) {
            hints.add("dinner");
        }
        if (!hints.contains("lunch") && recipe.getCalories() >= 300 && recipe.getCalories() <= 900) {
            hints.add("lunch");
        }

        return hints.stream().distinct().limit(3).toList();
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

    private double parseQuantity(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            String cleaned = value.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^0-9./\\s-]", " ")
                    .trim();
            if (cleaned.isBlank()) return 0.0;

            String[] rangeParts = cleaned.split("\\s*-\\s*");
            if (rangeParts.length == 2) {
                double left = parseSingleQuantity(rangeParts[0]);
                double right = parseSingleQuantity(rangeParts[1]);
                if (left > 0 && right > 0) return (left + right) / 2.0;
                return Math.max(left, right);
            }

            return parseSingleQuantity(cleaned);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double parseSingleQuantity(String value) {
        if (value == null || value.isBlank()) return 0.0;

        String trimmed = value.trim();
        if (trimmed.contains(" ")) {
            double total = 0.0;
            for (String part : trimmed.split("\\s+")) {
                total += parseSingleQuantity(part);
            }
            return total;
        }

        if (trimmed.contains("/")) {
            String[] fraction = trimmed.split("/");
            if (fraction.length == 2) {
                double numerator = safeParseDouble(fraction[0]);
                double denominator = safeParseDouble(fraction[1]);
                if (numerator > 0 && denominator > 0) return numerator / denominator;
            }
            return 0.0;
        }

        return safeParseDouble(trimmed.replaceAll("[^0-9.]", ""));
    }

    private double safeParseDouble(String value) {
        try {
            if (value == null || value.isBlank()) return 0.0;
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private boolean containsAnyText(String text, String... needles) {
        if (text == null || text.isBlank()) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
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
            String theme,
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
