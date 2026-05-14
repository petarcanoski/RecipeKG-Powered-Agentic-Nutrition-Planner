package com.recipekg.planner.model;

import java.util.List;

public record RecipeBrief(
        String id,
        String label,
        List<String> mealHints,
        List<String> keyIngredients,
        MacroSummary macrosPerServing,
        double servings,
        double performanceScore,
        double macroConfidence,
        double nutritionistPromptScore,
        List<String> unresolvedIngredients
) {}
