package com.recipekg.planner.service;

import com.recipekg.planner.model.UserProfile;
import lombok.*;

import org.springframework.stereotype.Service;

@Service
public class MacroCalculatorService {

    public MacroTargets calculate(UserProfile p) {

        double bmr;
        double weight = p.getWeight() == null ? 0.0 : p.getWeight();
        double height = p.getHeight() == null ? 0.0 : p.getHeight();
        int age = p.getAge() == null ? 0 : p.getAge();
        String gender = p.getGender() == null ? "" : p.getGender();
        String activityLevel = p.getActivityLevel() == null ? "" : p.getActivityLevel().toUpperCase();
        String goal = p.getGoal() == null ? "" : p.getGoal().toUpperCase();

        if ("MALE".equalsIgnoreCase(gender)) {

            bmr =
                    10 * weight +
                            6.25 * height -
                            5 * age +
                            5;

        } else {

            bmr =
                    10 * weight +
                            6.25 * height -
                            5 * age -
                            161;
        }

        double activityMultiplier =
                switch (activityLevel) {

                    case "LOW" -> 1.3;
                    case "MEDIUM" -> 1.55;
                    case "HIGH" -> 1.8;
                    default -> 1.4;
                };

        double tdee = bmr * activityMultiplier;

        if (goal.contains("GAIN") || goal.contains("MUSCLE") || goal.contains("HYPERTROPHY") || goal.contains("BULK"))
            tdee += 400;

        if (goal.contains("LOSE") || goal.contains("LOSS") || goal.contains("CUT") || goal.contains("FAT"))
            tdee -= 400;

        double protein =
                weight * 2.0;

        double fats =
                tdee * 0.25 / 9;

        double carbs =
                (tdee - protein * 4 - fats * 9) / 4;

        return new MacroTargets(
                tdee,
                protein,
                carbs,
                fats
        );
    }

    @Getter
    @AllArgsConstructor
    public static class MacroTargets {

        private double calories;
        private double protein;
        private double carbs;
        private double fats;
    }
}
