package com.recipekg.planner.response;

import com.recipekg.planner.model.MacroSummary;

import java.util.List;

public record FrontendDailyNutritionPlanResponse(
        int day,
        List<FrontendMealPlanResponse> meals,
        MacroSummary totalMacros,
        String rationale
) {}
