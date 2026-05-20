package com.recipekg.planner.model;

import java.util.List;

public record NutritionPlan(
        String goalStatus,
        String summary,
        List<DailyMealPlan> days,
        MacroSummary weeklyTotals,
        PlanningTrace planningTrace
) {}
