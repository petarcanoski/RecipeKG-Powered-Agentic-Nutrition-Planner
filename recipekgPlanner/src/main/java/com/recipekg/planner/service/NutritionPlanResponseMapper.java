package com.recipekg.planner.service;

import com.recipekg.planner.model.DailyMealPlan;
import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.PlannedMeal;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.response.FrontendDailyNutritionPlanResponse;
import com.recipekg.planner.response.FrontendMealPlanResponse;
import com.recipekg.planner.response.FrontendNutritionPlanResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NutritionPlanResponseMapper {

    public FrontendNutritionPlanResponse toFrontend(NutritionPlan plan, List<RecipeCandidate> recipes) {
        Map<String, RecipeCandidate> recipeByUri = recipes == null
                ? Map.of()
                : recipes.stream()
                .filter(recipe -> recipe.getUri() != null)
                .collect(Collectors.toMap(
                        RecipeCandidate::getUri,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<FrontendDailyNutritionPlanResponse> days = plan.days() == null
                ? List.of()
                : plan.days().stream()
                .map(day -> mapDay(day, recipeByUri))
                .toList();

        return new FrontendNutritionPlanResponse(
                plan.goalStatus(),
                plan.summary(),
                days,
                plan.weeklyTotals()
        );
    }

    private FrontendDailyNutritionPlanResponse mapDay(
            DailyMealPlan day,
            Map<String, RecipeCandidate> recipeByUri
    ) {
        List<FrontendMealPlanResponse> meals = day.meals() == null
                ? List.of()
                : day.meals().stream()
                .map(meal -> mapMeal(meal, recipeByUri))
                .toList();

        return new FrontendDailyNutritionPlanResponse(
                day.day(),
                meals,
                day.estimatedTotals(),
                day.dayRationale()
        );
    }

    private FrontendMealPlanResponse mapMeal(
            PlannedMeal meal,
            Map<String, RecipeCandidate> recipeByUri
    ) {
        RecipeCandidate recipe = recipeByUri.get(meal.recipeUri());
        String recipeName = recipe != null && recipe.getLabel() != null
                ? recipe.getLabel()
                : meal.recipeLabel();

        List<String> ingredients = recipe == null || recipe.getIngredients() == null
                ? List.of()
                : recipe.getIngredients().stream()
                .map(this::formatIngredient)
                .filter(ingredient -> !ingredient.isBlank())
                .toList();

        return new FrontendMealPlanResponse(
                meal.slot(),
                recipeName,
                ingredients,
                meal.servings(),
                meal.estimatedMacros(),
                meal.reason()
        );
    }

    private String formatIngredient(IngredientUse ingredient) {
        if (ingredient == null) {
            return "";
        }

        return Stream.of(ingredient.getQuantity(), ingredient.getUnit(), ingredient.getName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" "));
    }
}
