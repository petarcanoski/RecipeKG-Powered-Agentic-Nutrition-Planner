package com.recipekg.planner.service;

import com.recipekg.planner.dto.MealDTO;
import com.recipekg.planner.dto.UriWeekPlan;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DiseaseBrainService {

    public boolean isSafe(UriWeekPlan plan, List<MealDTO> meals, String diseases) {

        String d = diseases.toLowerCase();

        if (d.contains("diabetes")) {
            if (containsIngredient(plan, meals, "sugar")) return false;
        }

        if (d.contains("hypertension")) {
            if (containsIngredient(plan, meals, "salt")) return false;
        }

        if (d.contains("cholesterol")) {
            if (containsIngredient(plan, meals, "butter")) return false;
        }

        return true;
    }

    private boolean containsIngredient(
            UriWeekPlan plan,
            List<MealDTO> meals,
            String keyword
    ) {

        Set<String> usedUris = new HashSet<>();

        for (var d : plan.getWeekPlan()) {
            usedUris.add(d.getBreakfast());
            usedUris.add(d.getLunch());
            usedUris.add(d.getDinner());
        }

        for (MealDTO m : meals) {

            if (usedUris.contains(m.getMealUri())) {

                for (var ing : m.getIngredients()) {

                    if (ing.getName().toLowerCase().contains(keyword)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
