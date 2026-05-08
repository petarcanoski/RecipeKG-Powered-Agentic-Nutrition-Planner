package com.recipekg.planner.service;

import com.recipekg.planner.model.NutrientTarget;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.RecipeCandidate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PerformanceScoringService {

    public List<RecipeCandidate> scoreAndRank(List<RecipeCandidate> recipes, PerformanceManifest manifest) {
        if (recipes == null || recipes.isEmpty()) return recipes;

        for (RecipeCandidate recipe : recipes) {
            score(recipe, manifest);
        }

        return recipes.stream()
                .sorted(Comparator.comparingDouble(RecipeCandidate::getPerformanceScore).reversed())
                .toList();
    }

    private void score(RecipeCandidate recipe, PerformanceManifest manifest) {
        String goal = manifest == null || manifest.goalStatus() == null
                ? "MAINTENANCE"
                : manifest.goalStatus().trim().toUpperCase(Locale.ROOT);



        NutrientTarget proteinTarget = findTarget(manifest, "protein");
        NutrientTarget calorieTarget = findTarget(manifest, "calories");
        NutrientTarget carbTarget = findTarget(manifest, "carbs");
        NutrientTarget fatTarget = findTarget(manifest, "fat");

        double score;
        String reason;

        if ("HYPERTROPHY".equals(goal) || "ENDURANCE".equals(goal)) {
            double proteinScore = minimumScore(recipe.getProtein(), proteinTarget);
            double calorieScore = rangeScore(recipe.getCalories(), calorieTarget);
            double carbScore = rangeScore(recipe.getCarbs(), carbTarget);
            double fatScore = rangeScore(recipe.getFat(), fatTarget);
            score = 0.42 * proteinScore
                    + 0.26 * calorieScore
                    + 0.14 * carbScore
                    + 0.10 * fatScore
                    + 0.08 * proteinDensityScore(recipe);
            score -= softSodiumPenalty(recipe) + softSugarPenalty(recipe);
            reason = "Ranked for higher protein and useful energy per serving.";
        } else if ("FAT_LOSS".equals(goal)) {
            double proteinScore = minimumScore(recipe.getProtein(), proteinTarget);
            double densityScore = proteinDensityScore(recipe);
            double calorieScore = upperBoundScore(recipe.getCalories(), calorieTarget);
            score = 0.42 * proteinScore
                    + 0.34 * densityScore
                    + 0.24 * calorieScore;
            score -= softSodiumPenalty(recipe) + softSugarPenalty(recipe);
            reason = "Ranked for protein density and moderate calories per serving.";
        } else {
            double proteinScore = rangeScore(recipe.getProtein(), proteinTarget);
            double calorieScore = rangeScore(recipe.getCalories(), calorieTarget);
            double carbScore = rangeScore(recipe.getCarbs(), carbTarget);
            double fatScore = rangeScore(recipe.getFat(), fatTarget);
            score = 0.30 * calorieScore
                    + 0.28 * proteinScore
                    + 0.22 * carbScore
                    + 0.20 * fatScore;
            score -= softSodiumPenalty(recipe) + softSugarPenalty(recipe);
            reason = "Ranked for balanced per-serving macro fit.";
        }

        recipe.setPerformanceScore(clamp(score, 0.0, 1.0));
        recipe.setPerformanceReason(reason);
    }

    private NutrientTarget findTarget(PerformanceManifest manifest, String nutrient) {
        if (manifest == null || manifest.perServingTargets() == null) return null;

        return manifest.perServingTargets().stream()
                .filter(target -> target.nutrient() != null)
                .filter(target -> normalize(target.nutrient()).contains(normalize(nutrient)))
                .findFirst()
                .orElse(null);
    }

    private double minimumScore(double value, NutrientTarget target) {
        double min = numberOrDefault(target == null ? null : target.min(), 0.0);
        double ideal = numberOrDefault(target == null ? null : target.target(), min);

        if (min <= 0 && ideal <= 0) return value > 0 ? 0.6 : 0.0;
        if (value >= ideal && ideal > 0) return 1.0;
        if (value >= min && ideal > min) return 0.75 + 0.25 * ((value - min) / (ideal - min));
        return min > 0 ? clamp(value / min, 0.0, 0.75) : 0.0;
    }

    private double upperBoundScore(double value, NutrientTarget target) {
        double min = numberOrDefault(target == null ? null : target.min(), 0.0);
        double max = numberOrDefault(target == null ? null : target.max(), 0.0);

        if (max <= 0) return value > 0 ? 0.6 : 0.0;
        if (value <= max && value >= min) return 1.0;
        if (value < min && min > 0) return clamp(value / min, 0.0, 1.0);
        return clamp(max / value, 0.0, 1.0);
    }

    private double rangeScore(double value, NutrientTarget target) {
        if (target == null) return value > 0 ? 0.5 : 0.0;

        double min = numberOrDefault(target.min(), 0.0);
        double targetValue = numberOrDefault(target.target(), 0.0);
        double max = numberOrDefault(target.max(), 0.0);

        if (min > 0 && max > 0 && value >= min && value <= max) return 1.0;
        if (targetValue > 0) {
            double distance = Math.abs(value - targetValue) / targetValue;
            return clamp(1.0 - distance, 0.0, 1.0);
        }
        if (min > 0 && value < min) return clamp(value / min, 0.0, 1.0);
        if (max > 0 && value > max) return clamp(max / value, 0.0, 1.0);
        return value > 0 ? 0.5 : 0.0;
    }

    private double proteinDensityScore(RecipeCandidate recipe) {
        if (recipe.getCalories() <= 0) return 0.0;
        double gramsPer100Kcal = recipe.getProtein() / recipe.getCalories() * 100.0;
        return clamp(gramsPer100Kcal / 10.0, 0.0, 1.0);
    }

    private double softSodiumPenalty(RecipeCandidate recipe) {
        if (recipe.getSodium() <= 1000.0) return 0.0;
        return clamp((recipe.getSodium() - 1000.0) / 3000.0, 0.0, 0.18);
    }

    private double softSugarPenalty(RecipeCandidate recipe) {
        if (recipe.getSugar() <= 30.0) return 0.0;
        return clamp((recipe.getSugar() - 30.0) / 80.0, 0.0, 0.12);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private double numberOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
