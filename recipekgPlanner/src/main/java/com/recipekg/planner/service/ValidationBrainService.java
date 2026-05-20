package com.recipekg.planner.service;

import com.recipekg.planner.model.DailyMealPlan;
import com.recipekg.planner.model.MacroSummary;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutrientCap;
import com.recipekg.planner.model.NutrientTarget;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.PlannedMeal;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.ValidationIssue;
import com.recipekg.planner.model.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ValidationBrainService {

    public ValidationResult validate(
            NutritionPlan plan,
            PerformanceManifest performanceManifest,
            MedicalManifest medicalManifest,
            Map<String, RecipeCandidate> recipeById
    ) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (plan == null || plan.days() == null || plan.days().size() != 7) {
            issues.add(new ValidationIssue(
                    "PLAN_SHAPE",
                    0,
                    "HIGH",
                    "Plan must contain exactly 7 days.",
                    "Return a complete 7-day plan."
            ));
            return new ValidationResult("REVISE", issues);
        }

        validateDailyShape(plan, issues);
        validateDailyTargets(plan, performanceManifest, issues);
        validateMedicalCaps(plan, medicalManifest, issues);
        validateRepetition(plan, issues);

        return new ValidationResult(issues.isEmpty() ? "PASS" : "REVISE", issues);
    }

    private void validateDailyShape(NutritionPlan plan, List<ValidationIssue> issues) {
        for (DailyMealPlan day : plan.days()) {
            if (day.meals() == null || day.meals().size() < 3) {
                issues.add(new ValidationIssue(
                        "TOO_FEW_MEALS",
                        day.day(),
                        "HIGH",
                        "Day has fewer than 3 meals.",
                        "Add enough meals to cover breakfast, lunch, and dinner."
                ));
                continue;
            }

            Set<String> slots = day.meals().stream()
                    .map(meal -> normalize(meal.slot()))
                    .collect(Collectors.toSet());

            if (!slots.contains("breakfast")) {
                issues.add(new ValidationIssue(
                        "MISSING_BREAKFAST",
                        day.day(),
                        "MEDIUM",
                        "Day is missing breakfast.",
                        "Add a breakfast-suitable recipe or a smaller breakfast serving."
                ));
            }
            if (!slots.contains("lunch")) {
                issues.add(new ValidationIssue(
                        "MISSING_LUNCH",
                        day.day(),
                        "HIGH",
                        "Day is missing lunch.",
                        "Add a balanced lunch recipe."
                ));
            }
            if (!slots.contains("dinner")) {
                issues.add(new ValidationIssue(
                        "MISSING_DINNER",
                        day.day(),
                        "HIGH",
                        "Day is missing dinner.",
                        "Add a complete dinner entree."
                ));
            }
        }
    }

    private void validateDailyTargets(NutritionPlan plan, PerformanceManifest manifest, List<ValidationIssue> issues) {
        if (manifest == null || manifest.dailyTargets() == null) return;

        NutrientTarget calories = target(manifest, "calories");
        NutrientTarget protein = target(manifest, "protein");

        for (DailyMealPlan day : plan.days()) {
            MacroSummary totals = day.estimatedTotals();
            if (totals == null) continue;

            if (calories != null && calories.min() != null && totals.calories() < calories.min() * 0.80) {
                issues.add(new ValidationIssue(
                        "LOW_CALORIES",
                        day.day(),
                        "HIGH",
                        "Daily calories are far below the performance target.",
                        "Increase servings or add an energy-dense meal/snack."
                ));
            }
            if (calories != null && calories.max() != null && totals.calories() > calories.max() * 1.15) {
                issues.add(new ValidationIssue(
                        "HIGH_CALORIES",
                        day.day(),
                        "MEDIUM",
                        "Daily calories are above the performance target range.",
                        "Reduce serving sizes or swap one high-calorie meal."
                ));
            }
            if (protein != null && protein.min() != null && totals.protein() < protein.min() * 0.80) {
                issues.add(new ValidationIssue(
                        "LOW_PROTEIN",
                        day.day(),
                        "HIGH",
                        "Daily protein is far below the performance target.",
                        "Add or swap in a higher-protein recipe."
                ));
            }
        }
    }

    private void validateMedicalCaps(NutritionPlan plan, MedicalManifest manifest, List<ValidationIssue> issues) {
        if (manifest == null || manifest.constraints() == null || manifest.constraints().nutrientCaps() == null) return;

        for (DailyMealPlan day : plan.days()) {
            MacroSummary totals = day.estimatedTotals();
            if (totals == null) continue;

            for (NutrientCap cap : manifest.constraints().nutrientCaps()) {
                double value = macroByName(totals, cap.nutrient());
                if (value > cap.maxValue()) {
                    issues.add(new ValidationIssue(
                            "MEDICAL_CAP_EXCEEDED",
                            day.day(),
                            "HIGH",
                            cap.nutrient() + " exceeds the medical cap.",
                            "Replace meals or reduce servings to keep " + cap.nutrient() + " under the cap."
                    ));
                }
            }
        }
    }

    private void validateRepetition(NutritionPlan plan, List<ValidationIssue> issues) {
        Map<String, Integer> counts = new HashMap<>();
        for (DailyMealPlan day : plan.days()) {
            if (day.meals() == null) continue;
            for (PlannedMeal meal : day.meals()) {
                counts.merge(meal.recipeId(), 1, Integer::sum);
            }
        }

        counts.forEach((recipeId, count) -> {
            if (count > 2) {
                issues.add(new ValidationIssue(
                        "RECIPE_REPETITION",
                        0,
                        "HIGH",
                        "Recipe " + recipeId + " appears " + count + " times.",
                        "Replace repeated appearances with similar alternatives."
                ));
            }
        });
    }

    private NutrientTarget target(PerformanceManifest manifest, String nutrient) {
        return manifest.dailyTargets().stream()
                .filter(target -> normalize(target.nutrient()).contains(nutrient))
                .findFirst()
                .orElse(null);
    }

    private double macroByName(MacroSummary macros, String nutrientName) {
        String name = normalize(nutrientName);
        if (name.contains("calories") || name.contains("energy")) return macros.calories();
        if (name.contains("protein")) return macros.protein();
        if (name.contains("carb")) return macros.carbs();
        if (name.contains("fat") || name.contains("lipid")) return macros.fat();
        if (name.contains("sugar")) return macros.sugar();
        if (name.contains("sodium")) return macros.sodium();
        return 0.0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
