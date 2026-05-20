package com.recipekg.planner.response;

import com.recipekg.planner.model.MacroSummary;

import java.util.List;

public record FrontendNutritionPlanResponse(
        String goalStatus,
        String summary,
        List<FrontendDailyNutritionPlanResponse> days,
        MacroSummary weeklyTotals
) {}
