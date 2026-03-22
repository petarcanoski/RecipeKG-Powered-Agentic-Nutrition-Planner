package com.recipekg.planner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NutritionFacts {

    private double calories;
    private double protein;
    private double carbs;
    private double fats;
}