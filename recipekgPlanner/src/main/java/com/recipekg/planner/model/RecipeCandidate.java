package com.recipekg.planner.model;

import lombok.Data;

import java.util.List;
@Data
public class RecipeCandidate {
    private String uri;
    private String label;
    private List<String> usdaIngredientIds;
    private String usdaIngredientText = "";
    private double calories;
    private double protein;
    private double carbs;
    private double fat;
    private double sugar;
    private double sodium;

    public RecipeCandidate(String uri, String label, List<String> usdaIngredientIds) {
        this.uri = uri;
        this.label = label;
        this.usdaIngredientIds = usdaIngredientIds;
    }
}