package com.recipekg.planner.model;

import java.util.List;

public record DailyMealPlan(
        int day,
        List<PlannedMeal> meals,
        MacroSummary estimatedTotals,
        String dayRationale
) {}
