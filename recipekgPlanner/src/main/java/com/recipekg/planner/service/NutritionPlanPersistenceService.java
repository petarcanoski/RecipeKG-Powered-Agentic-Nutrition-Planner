package com.recipekg.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.MacroSummary;
import com.recipekg.planner.model.NutritionPlanDayEntity;
import com.recipekg.planner.model.NutritionPlanEntity;
import com.recipekg.planner.model.NutritionPlanMealEntity;
import com.recipekg.planner.model.User;
import com.recipekg.planner.repository.NutritionPlanRepository;
import com.recipekg.planner.response.FrontendDailyNutritionPlanResponse;
import com.recipekg.planner.response.FrontendMealPlanResponse;
import com.recipekg.planner.response.FrontendNutritionPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NutritionPlanPersistenceService {

    public static final String RECIPE_KG_AGENT = "RECIPE_KG_AGENT";
    public static final String DIRECT_GEMINI = "DIRECT_GEMINI";
    public static final String TEST_MOCK = "TEST_MOCK";

    private final NutritionPlanRepository nutritionPlanRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public NutritionPlanEntity save(
            User user,
            int weekNumber,
            LocalDate startDate,
            FrontendNutritionPlanResponse response
    ) {
        return save(user, weekNumber, startDate, response, RECIPE_KG_AGENT);
    }

    @Transactional
    public NutritionPlanEntity save(
            User user,
            int weekNumber,
            LocalDate startDate,
            FrontendNutritionPlanResponse response,
            String generatedBy
    ) {
        String source = normalizeGeneratedBy(generatedBy);
        nutritionPlanRepository.findByUserIdAndWeekNumberAndGeneratedBy(user.getId(), weekNumber, source)
                .ifPresent(existingPlan -> {
                    nutritionPlanRepository.delete(existingPlan);
                    nutritionPlanRepository.flush();
                });

        NutritionPlanEntity plan = new NutritionPlanEntity();

        plan.setUser(user);
        plan.setWeekNumber(weekNumber);
        plan.setStartDate(startDate);
        plan.setGoalStatus(response.goalStatus());
        plan.setGeneratedBy(source);
        plan.setSummary(response.summary());
        applyWeeklyMacros(plan, response.weeklyTotals());

        if (response.days() != null) {
            response.days().forEach(dayResponse -> plan.getDays().add(toDayEntity(plan, dayResponse)));
        }

        return nutritionPlanRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendNutritionPlanResponse> findByUserWeek(Long userId, int weekNumber) {
        return nutritionPlanRepository.findTopByUserIdAndWeekNumberOrderByUpdatedAtDesc(userId, weekNumber)
                .map(this::toFrontendResponse);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendNutritionPlanResponse> findLatestByUser(Long userId) {
        return nutritionPlanRepository.findTopByUserIdOrderByWeekNumberDescUpdatedAtDesc(userId)
                .map(this::toFrontendResponse);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendNutritionPlanResponse> findByUserWeekAndGeneratedBy(Long userId, int weekNumber, String generatedBy) {
        return nutritionPlanRepository.findByUserIdAndWeekNumberAndGeneratedBy(userId, weekNumber, normalizeGeneratedBy(generatedBy))
                .map(this::toFrontendResponse);
    }

    @Transactional(readOnly = true)
    public Optional<FrontendNutritionPlanResponse> findLatestByUserAndGeneratedBy(Long userId, String generatedBy) {
        return nutritionPlanRepository.findTopByUserIdAndGeneratedByOrderByWeekNumberDescUpdatedAtDesc(userId, normalizeGeneratedBy(generatedBy))
                .map(this::toFrontendResponse);
    }

    private FrontendNutritionPlanResponse toFrontendResponse(NutritionPlanEntity plan) {
        List<FrontendDailyNutritionPlanResponse> days = plan.getDays() == null
                ? List.of()
                : plan.getDays().stream()
                .sorted(Comparator.comparing(NutritionPlanDayEntity::getDayNumber))
                .map(this::toFrontendDay)
                .toList();

        return new FrontendNutritionPlanResponse(
                plan.getGoalStatus(),
                plan.getSummary(),
                days,
                new MacroSummary(
                        safeDouble(plan.getWeeklyCalories()),
                        safeDouble(plan.getWeeklyProtein()),
                        safeDouble(plan.getWeeklyCarbs()),
                        safeDouble(plan.getWeeklyFat()),
                        safeDouble(plan.getWeeklySugar()),
                        safeDouble(plan.getWeeklySodium())
                )
        );
    }

    private FrontendDailyNutritionPlanResponse toFrontendDay(NutritionPlanDayEntity day) {
        List<FrontendMealPlanResponse> meals = day.getMeals() == null
                ? List.of()
                : day.getMeals().stream()
                .sorted(Comparator.comparing(NutritionPlanMealEntity::getSortOrder))
                .map(this::toFrontendMeal)
                .toList();

        return new FrontendDailyNutritionPlanResponse(
                day.getDayNumber(),
                meals,
                new MacroSummary(
                        safeDouble(day.getTotalCalories()),
                        safeDouble(day.getTotalProtein()),
                        safeDouble(day.getTotalCarbs()),
                        safeDouble(day.getTotalFat()),
                        safeDouble(day.getTotalSugar()),
                        safeDouble(day.getTotalSodium())
                ),
                day.getRationale()
        );
    }

    private FrontendMealPlanResponse toFrontendMeal(NutritionPlanMealEntity meal) {
        return new FrontendMealPlanResponse(
                meal.getSlot(),
                meal.getRecipeName(),
                readIngredients(meal.getIngredientsJson()),
                safeDouble(meal.getServings()),
                new MacroSummary(
                        safeDouble(meal.getCalories()),
                        safeDouble(meal.getProtein()),
                        safeDouble(meal.getCarbs()),
                        safeDouble(meal.getFat()),
                        safeDouble(meal.getSugar()),
                        safeDouble(meal.getSodium())
                ),
                meal.getReason()
        );
    }

    private NutritionPlanDayEntity toDayEntity(
            NutritionPlanEntity plan,
            FrontendDailyNutritionPlanResponse response
    ) {
        NutritionPlanDayEntity day = new NutritionPlanDayEntity();
        day.setNutritionPlan(plan);
        day.setDayNumber(response.day());
        day.setRationale(response.rationale());
        applyDailyMacros(day, response.totalMacros());

        List<FrontendMealPlanResponse> meals = response.meals() == null
                ? List.of()
                : response.meals();

        for (int index = 0; index < meals.size(); index++) {
            day.getMeals().add(toMealEntity(day, meals.get(index), index));
        }

        return day;
    }

    private NutritionPlanMealEntity toMealEntity(
            NutritionPlanDayEntity day,
            FrontendMealPlanResponse response,
            int index
    ) {
        NutritionPlanMealEntity meal = new NutritionPlanMealEntity();
        meal.setDay(day);
        meal.setSlot(response.slot());
        meal.setRecipeName(response.recipeName());
        meal.setServings(response.servings());
        meal.setIngredientsJson(writeIngredients(response.ingredients()));
        meal.setReason(response.reason());
        meal.setSortOrder(index);
        applyMealMacros(meal, response.totalMacros());
        return meal;
    }

    private void applyWeeklyMacros(NutritionPlanEntity plan, MacroSummary macros) {
        MacroSummary safeMacros = macros == null ? MacroSummary.zero() : macros;
        plan.setWeeklyCalories(safeMacros.calories());
        plan.setWeeklyProtein(safeMacros.protein());
        plan.setWeeklyCarbs(safeMacros.carbs());
        plan.setWeeklyFat(safeMacros.fat());
        plan.setWeeklySugar(safeMacros.sugar());
        plan.setWeeklySodium(safeMacros.sodium());
    }

    private void applyDailyMacros(NutritionPlanDayEntity day, MacroSummary macros) {
        MacroSummary safeMacros = macros == null ? MacroSummary.zero() : macros;
        day.setTotalCalories(safeMacros.calories());
        day.setTotalProtein(safeMacros.protein());
        day.setTotalCarbs(safeMacros.carbs());
        day.setTotalFat(safeMacros.fat());
        day.setTotalSugar(safeMacros.sugar());
        day.setTotalSodium(safeMacros.sodium());
    }

    private void applyMealMacros(NutritionPlanMealEntity meal, MacroSummary macros) {
        MacroSummary safeMacros = macros == null ? MacroSummary.zero() : macros;
        meal.setCalories(safeMacros.calories());
        meal.setProtein(safeMacros.protein());
        meal.setCarbs(safeMacros.carbs());
        meal.setFat(safeMacros.fat());
        meal.setSugar(safeMacros.sugar());
        meal.setSodium(safeMacros.sodium());
    }

    private String writeIngredients(List<String> ingredients) {
        try {
            return objectMapper.writeValueAsString(ingredients == null ? List.of() : ingredients);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize nutrition plan ingredients", e);
        }
    }

    private List<String> readIngredients(String ingredientsJson) {
        if (ingredientsJson == null || ingredientsJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(ingredientsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private String normalizeGeneratedBy(String generatedBy) {
        return generatedBy == null || generatedBy.isBlank()
                ? RECIPE_KG_AGENT
                : generatedBy.trim().toUpperCase();
    }
}
