package com.recipekg.planner.model;

import java.util.List;

public record NutritionistSelectionState(
        List<String> selectedRecipeIds,
        List<String> rejectedRecipeIds,
        List<RecipeSubstitution> substitutions,
        List<String> missingNeeds,
        List<String> nutritionConcerns,
        List<String> planningNotes
) {
    public static NutritionistSelectionState empty() {
        return new NutritionistSelectionState(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
