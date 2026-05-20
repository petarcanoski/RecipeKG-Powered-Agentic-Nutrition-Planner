package com.recipekg.planner.response;

import com.recipekg.planner.model.MacroSummary;

import java.util.List;

public record FrontendMealPlanResponse(
        String slot,
        String recipeName,
        List<String> ingredients,
        double servings,
        MacroSummary totalMacros,
        String reason
) {}
