package com.recipekg.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MedicalManifest(
        @JsonProperty("status") String status,
        @JsonProperty("constraints") DietaryConstraints constraints,
        @JsonProperty("medical_rationale") String medicalRationale
) {}