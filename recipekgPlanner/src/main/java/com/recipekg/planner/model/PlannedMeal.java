package com.recipekg.planner.model;

public record PlannedMeal(
        String slot,
        String recipeId,
        String recipeUri,
        String recipeLabel,
        double servings,
        MacroSummary estimatedMacros,
        String reason
) {}
