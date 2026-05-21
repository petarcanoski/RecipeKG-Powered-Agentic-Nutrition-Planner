package com.recipekg.planner.dto;

import lombok.Data;

@Data
public class PlanComparisonVoteRequest {
    private Long userId;
    private String winner;
    private String reason;
}
