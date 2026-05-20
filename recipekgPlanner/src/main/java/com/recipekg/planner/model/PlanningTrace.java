package com.recipekg.planner.model;

import java.util.List;

public record PlanningTrace(
        String sessionId,
        String finalStatus,
        List<PlanningIterationTrace> iterations
) {}
