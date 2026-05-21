package com.recipekg.planner.response;

public record PlanComparisonVoteResponse(
        Long id,
        Long userId,
        Long recipeKgPlanId,
        Long geminiPlanId,
        String winner,
        String reason,
        String message
) {
}
