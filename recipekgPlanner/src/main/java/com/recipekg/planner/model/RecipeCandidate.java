package com.recipekg.planner.model;

import lombok.Setter;

import java.util.List;

public class RecipeCandidate {
    private String uri;
    private String label;
    private List<String> usdaIngredientIds;
    // Setters for the Macros
    @Setter
    private double calories;
    @Setter
    private double protein;
    @Setter
    private double carbs;
    @Setter
    private double fat;

    public RecipeCandidate(String uri, String label, List<String> usdaIngredientIds) {
        this.uri = uri;
        this.label = label;
        this.usdaIngredientIds = usdaIngredientIds;
    }

    // Getters
    public String getUri() { return uri; }
    public String getLabel() { return label; }
    public List<String> getUsdaIngredientIds() { return usdaIngredientIds; }
    public double getCalories() { return calories; }
    public double getProtein() { return protein; }
    public double getCarbs() { return carbs; }
    public double getFat() { return fat; }

}