package com.recipekg.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NutrientTarget(
        @JsonProperty("nutrient") String nutrient,
        @JsonProperty("min") Double min,
        @JsonProperty("target") Double target,
        @JsonProperty("max") Double max,
        @JsonProperty("unit") String unit,
        @JsonProperty("period") String period
) {}
