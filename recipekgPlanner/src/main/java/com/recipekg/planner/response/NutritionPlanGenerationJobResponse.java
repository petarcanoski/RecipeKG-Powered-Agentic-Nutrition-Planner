package com.recipekg.planner.response;

import java.time.LocalDateTime;

public record NutritionPlanGenerationJobResponse(
        String jobId,
        Long userId,
        String status,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        FrontendNutritionPlanResponse nutritionPlan
) {}
