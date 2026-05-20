package com.recipekg.planner.model;

import java.util.List;

public record PlanningIterationTrace(
        int iterationNumber,
        String phase,
        List<String> recipeIdsSent,
        List<String> selectedRecipeIds,
        ValidationResult validationResult,
        String status
) {}
