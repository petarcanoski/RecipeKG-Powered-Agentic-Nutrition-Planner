package com.recipekg.planner.model;

import java.util.List;

public record PlanningIterationTrace(
        int iterationNumber,
        String phase,
        List<String> recipeIdsSent,
        List<String> selectedRecipeIds,
        ValidationResult validationResult,
        String status,
        String promptText,
        String responseText,
        String errorMessage,
        String model
) {
    public PlanningIterationTrace(
            int iterationNumber,
            String phase,
            List<String> recipeIdsSent,
            List<String> selectedRecipeIds,
            ValidationResult validationResult,
            String status
    ) {
        this(
                iterationNumber,
                phase,
                recipeIdsSent,
                selectedRecipeIds,
                validationResult,
                status,
                null,
                null,
                null,
                null
        );
    }
}
