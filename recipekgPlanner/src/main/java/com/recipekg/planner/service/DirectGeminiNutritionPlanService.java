package com.recipekg.planner.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.MacroSummary;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.UserProfileRepository;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.response.FrontendDailyNutritionPlanResponse;
import com.recipekg.planner.response.FrontendMealPlanResponse;
import com.recipekg.planner.response.FrontendNutritionPlanResponse;
import com.recipekg.planner.service.ai.NvidiaChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DirectGeminiNutritionPlanService {

    private final NvidiaChatClient nvidiaChatClient;
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final NutritionPlanPersistenceService nutritionPlanPersistenceService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FrontendNutritionPlanResponse generateAndSave(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow();
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow();

        FrontendNutritionPlanResponse plan = generate(profile);
        int weekNumber = resolveWeekNumber(user);
        nutritionPlanPersistenceService.save(
                user,
                weekNumber,
                resolveStartDate(user, weekNumber),
                plan,
                NutritionPlanPersistenceService.DIRECT_GEMINI
        );

        return plan;
    }

    private FrontendNutritionPlanResponse generate(UserProfile profile) {
        String rawText = callModel(buildPrompt(profile));
        try {
            return normalizePlan(parsePlan(rawText));
        } catch (Exception e) {
            throw new RuntimeException("Direct NVIDIA nutrition plan parse failed", e);
        }
    }

    private String buildPrompt(UserProfile profile) {
        return """
You are a nutrition planning assistant.

Create a practical 7-day nutrition plan directly from the user's profile.
Use normal meal names and ingredient lists that a user could understand.

Return strict JSON only. The JSON must match this exact shape:
{
  "goalStatus": "string",
  "summary": "string",
  "days": [
    {
      "day": 1,
      "meals": [
        {
          "slot": "breakfast|lunch|dinner|snack|dessert",
          "recipeName": "string",
          "ingredients": ["string"],
          "servings": 1.0,
          "totalMacros": {
            "calories": 0,
            "protein": 0,
            "carbs": 0,
            "fat": 0,
            "sugar": 0,
            "sodium": 0
          },
          "reason": "string"
        }
      ],
      "totalMacros": {
        "calories": 0,
        "protein": 0,
        "carbs": 0,
        "fat": 0,
        "sugar": 0,
        "sodium": 0
      },
      "rationale": "string"
    }
  ],
  "weeklyTotals": {
    "calories": 0,
    "protein": 0,
    "carbs": 0,
    "fat": 0,
    "sugar": 0,
    "sodium": 0
  }
}

Rules:
1. Return exactly 7 days.
2. Each day should have breakfast, lunch, and dinner. Add snacks only when useful.
3. Respect allergies and diseases/conditions strictly.
4. If the user has diabetes or sugar restriction, keep sugar conservative.
5. If the user has hypertension or sodium restriction, keep sodium conservative.
6. Macros must be realistic estimates for the listed servings.
7. Compute day totalMacros as the sum of meal totalMacros.
8. Compute weeklyTotals as the sum of day totalMacros.
9. Do not include markdown fences or explanation outside JSON.

USER_PROFILE:
Age: %s
Gender: %s
HeightCm: %s
WeightKg: %s
BloodType: %s
ActivityLevel: %s
Goal: %s
Allergies: %s
DiseasesOrConditions: %s
""".formatted(
                safe(profile.getAge()),
                safe(profile.getGender()),
                safe(profile.getHeight()),
                safe(profile.getWeight()),
                safe(profile.getBloodType()),
                safe(profile.getActivityLevel()),
                safe(profile.getGoal()),
                profile.getAllergies() == null ? List.of() : profile.getAllergies(),
                profile.getDiseases() == null ? List.of() : profile.getDiseases()
        );
    }

    private FrontendNutritionPlanResponse parsePlan(String jsonText) throws Exception {
        JsonNode node = mapper.readTree(extractJsonPayload(jsonText));
        if (node.isArray() && !node.isEmpty()) {
            node = node.get(0);
        }
        return mapper.treeToValue(node, FrontendNutritionPlanResponse.class);
    }

    private FrontendNutritionPlanResponse normalizePlan(FrontendNutritionPlanResponse plan) {
        List<FrontendDailyNutritionPlanResponse> days = plan.days() == null
                ? List.of()
                : plan.days().stream()
                .map(this::normalizeDay)
                .toList();

        if (days.size() != 7) {
            throw new IllegalArgumentException("Direct Gemini plan must contain exactly 7 days.");
        }

        MacroSummary weeklyTotals = days.stream()
                .map(FrontendDailyNutritionPlanResponse::totalMacros)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        return new FrontendNutritionPlanResponse(
                safe(plan.goalStatus()),
                safe(plan.summary()),
                days,
                weeklyTotals
        );
    }

    private FrontendDailyNutritionPlanResponse normalizeDay(FrontendDailyNutritionPlanResponse day) {
        List<FrontendMealPlanResponse> meals = day.meals() == null
                ? List.of()
                : day.meals().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeMeal)
                .toList();

        MacroSummary totalMacros = meals.stream()
                .map(FrontendMealPlanResponse::totalMacros)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        return new FrontendDailyNutritionPlanResponse(
                day.day(),
                meals,
                totalMacros,
                safe(day.rationale())
        );
    }

    private FrontendMealPlanResponse normalizeMeal(FrontendMealPlanResponse meal) {
        return new FrontendMealPlanResponse(
                safe(meal.slot()),
                safe(meal.recipeName()),
                meal.ingredients() == null ? List.of() : meal.ingredients(),
                meal.servings() <= 0 ? 1.0 : meal.servings(),
                meal.totalMacros() == null ? MacroSummary.zero() : meal.totalMacros(),
                safe(meal.reason())
        );
    }

    private String callModel(String prompt) {
        return nvidiaChatClient.complete(prompt);
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

        if (start < 0) return trimmed;

        char opener = trimmed.charAt(start);
        char closer = opener == '[' ? ']' : '}';
        int end = trimmed.lastIndexOf(closer);
        if (end <= start) return trimmed.substring(start).trim();

        return trimmed.substring(start, end + 1).trim();
    }

    private int resolveWeekNumber(User user) {
        Integer currentWeek = user.getCurrentWeek();
        return currentWeek == null || currentWeek < 1 ? 1 : currentWeek;
    }

    private LocalDate resolveStartDate(User user, int weekNumber) {
        return Objects.requireNonNullElse(user.getProgramStartDate(), LocalDate.now())
                .plusWeeks(Math.max(weekNumber - 1, 0));
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
