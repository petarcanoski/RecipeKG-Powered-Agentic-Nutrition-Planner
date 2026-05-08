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
    private boolean macroResolved = true;
    private String macroResolutionReason = "";

    public IngredientUse(String name, String quantity, String unit, String usdaUrl) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.usdaUrl = usdaUrl;
    }
}
