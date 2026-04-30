package com.recipekg.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NutrientCap(@JsonProperty("nutrient") String nutrient,
                          @JsonProperty("max_value") double maxValue,
                          @JsonProperty("unit") String unit) {
}
