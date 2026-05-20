package com.recipekg.planner.model;

import java.util.List;

public record ValidationResult(
        String status,
        List<ValidationIssue> issues
) {
    public boolean passed() {
        return "PASS".equalsIgnoreCase(status);
    }
}
