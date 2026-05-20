package com.recipekg.planner.model;

public record ValidationIssue(
        String type,
        int day,
        String severity,
        String message,
        String suggestion
) {}
