package com.recipekg.planner.model;

import java.util.List;

public record RecipeBrief(
        String id,
        String label,
        List<String> ingredients,
        MacroSummary macrosPerServing,
        double servings,
        List<String> unresolvedIngredients
) {}
