package com.recipekg.planner.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class RecipeCandidate {
    private String uri;
    private String label;
    private Double servings;
    private List<String> usdaIngredientIds;
    private String usdaIngredientText = "";
    private double calories;
    private double protein;
    private double carbs;
    private double fat;
    private double sugar;
    private double addedSugar;
    private double sodium;
    private List<IngredientUse> ingredients = new ArrayList<>();
    public RecipeCandidate(String uri, String label, List<String> usdaIngredientIds) {
        this.uri = uri;
        this.label = label;
        this.usdaIngredientIds = usdaIngredientIds;
    }
}