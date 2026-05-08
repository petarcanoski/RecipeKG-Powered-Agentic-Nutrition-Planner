package com.recipekg.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PerformanceManifest(
        @JsonProperty("goalStatus") String goalStatus,
        @JsonProperty("dailyTargets") List<NutrientTarget> dailyTargets,
        @JsonProperty("perServingTargets") List<NutrientTarget> perServingTargets,
        @JsonProperty("rationale") String rationale
) {}
