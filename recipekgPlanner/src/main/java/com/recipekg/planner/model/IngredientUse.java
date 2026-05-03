package com.recipekg.planner.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngredientUse {
    private String name;
    private String quantity;
    private String unit;
    private String usdaUrl;
}