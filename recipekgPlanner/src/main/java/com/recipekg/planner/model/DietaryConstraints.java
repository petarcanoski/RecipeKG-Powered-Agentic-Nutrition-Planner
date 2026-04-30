package com.recipekg.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DietaryConstraints(
        @JsonProperty("hard_exclusions") List<String> hardExclusions,
        @JsonProperty("nutrient_caps") List<NutrientCap> nutrientCaps,
        @JsonProperty("required_boosts") List<String> requiredBoosts
) {}
