package com.recipekg.planner.model;

public record MacroSummary(
        double calories,
        double protein,
        double carbs,
        double fat,
        double sugar,
        double sodium
) {
    public MacroSummary plus(MacroSummary other) {
        if (other == null) return this;
        return new MacroSummary(
                calories + other.calories(),
                protein + other.protein(),
                carbs + other.carbs(),
                fat + other.fat(),
                sugar + other.sugar(),
                sodium + other.sodium()
        );
    }

    public MacroSummary times(double multiplier) {
        return new MacroSummary(
                calories * multiplier,
                protein * multiplier,
                carbs * multiplier,
                fat * multiplier,
                sugar * multiplier,
                sodium * multiplier
        );
    }

    public static MacroSummary zero() {
        return new MacroSummary(0, 0, 0, 0, 0, 0);
    }
}
