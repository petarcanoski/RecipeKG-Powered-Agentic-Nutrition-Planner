package com.recipekg.planner.response;

public record NutritionPlanStatusResponse(
        String status,
        String message,
        String jobId,
        FrontendNutritionPlanResponse nutritionPlan
) {}
