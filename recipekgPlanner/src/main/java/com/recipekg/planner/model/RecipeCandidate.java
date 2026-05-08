package com.recipekg.planner.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private double sodium;
    private double performanceScore;
    private String performanceReason = "";
    private Map<String, String> macroUnits = Map.of(
            "calories", "kcal per serving",
            "protein", "g per serving",
            "carbs", "g per serving",
            "fat", "g per serving",
            "sugar", "g per serving",
            "sodium", "mg per serving"
    );
    private List<IngredientUse> ingredients = new ArrayList<>();
    public RecipeCandidate(String uri, String label, List<String> usdaIngredientIds) {
        this.uri = uri;
        this.label = label;
        this.usdaIngredientIds = usdaIngredientIds;
    }
}
