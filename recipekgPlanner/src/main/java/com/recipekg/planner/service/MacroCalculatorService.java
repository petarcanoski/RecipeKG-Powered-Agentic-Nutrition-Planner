package com.recipekg.planner.service;

import com.recipekg.planner.model.UserProfile;
import lombok.*;

import org.springframework.stereotype.Service;

@Service
public class MacroCalculatorService {

    public MacroTargets calculate(UserProfile p) {

        double bmr;

        if ("MALE".equalsIgnoreCase(p.getGender())) {

            bmr =
                    10 * p.getWeight() +
                            6.25 * p.getHeight() -
                            5 * p.getAge() +
                            5;

        } else {

            bmr =
                    10 * p.getWeight() +
                            6.25 * p.getHeight() -
                            5 * p.getAge() -
                            161;
        }

        double activityMultiplier =
                switch (p.getActivityLevel()) {

                    case "LOW" -> 1.3;
                    case "MEDIUM" -> 1.55;
                    case "HIGH" -> 1.8;
                    default -> 1.4;
                };

        double tdee = bmr * activityMultiplier;

        if ("GAIN".equalsIgnoreCase(p.getGoal()))
            tdee += 400;

        if ("LOSE".equalsIgnoreCase(p.getGoal()))
            tdee -= 400;

        double protein =
                p.getWeight() * 2.0;

        double fats =
                tdee * 0.25 / 9;

        double carbs =
                (tdee - protein * 4 - fats * 9) / 4;

        return new MacroTargets(
                Math.round(tdee),
                Math.round(protein),
                Math.round(carbs),
                Math.round(fats)
        );
    }

    @Getter
    @AllArgsConstructor
    public static class MacroTargets {

        private long calories;
        private long protein;
        private long carbs;
        private long fats;
    }
}