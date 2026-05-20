package com.recipekg.planner.model;

public record RecipeSubstitution(
        String removedRecipeId,
        String addedRecipeId,
        String reason
) {}
