package com.recipekg.planner.response;

public record PlanComparisonScoreResponse(
        long recipeKgWins,
        long geminiWins,
        long ties,
        long totalVotes
) {
}
