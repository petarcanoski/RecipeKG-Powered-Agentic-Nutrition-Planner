package com.recipekg.planner.service;

import com.recipekg.planner.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MacroBrainService {

    public NutritionFacts computeMealMacros(MealDTO meal) {

        double calories = 0;
        double protein = 0;
        double carbs = 0;
        double fats = 0;

        for (IngredientDTO ing : meal.getIngredients()) {

            double qty = parseQty(ing.getQuantity());

            // TEMP: simulated nutrition values
            calories += qty * 50;
            protein += qty * 3;
            carbs += qty * 5;
            fats += qty * 2;
        }

        return new NutritionFacts(calories, protein, carbs, fats);
    }

    private double parseQty(String q) {
        try {
            if (q == null || q.isBlank()) return 1;
            if (q.contains("/")) {
                String[] p = q.split("/");
                return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            }
            return Double.parseDouble(q);
        } catch (Exception e) {
            return 1;
        }
    }

    public NutritionFacts computeWeekMacros(UriWeekPlan plan, List<MealDTO> meals) {

        NutritionFacts total = new NutritionFacts(0,0,0,0);

        for (UriDayPlan d : plan.getWeekPlan()) {

            total = sum(total, find(d.getBreakfast(), meals));
            total = sum(total, find(d.getLunch(), meals));
            total = sum(total, find(d.getDinner(), meals));
        }

        return total;
    }

    private NutritionFacts find(String uri, List<MealDTO> meals) {

        MealDTO m = meals.stream()
                .filter(x -> x.getMealUri().equals(uri))
                .findFirst()
                .orElseThrow();

        return computeMealMacros(m);
    }

    private NutritionFacts sum(NutritionFacts a, NutritionFacts b) {

        return new NutritionFacts(
                a.getCalories() + b.getCalories(),
                a.getProtein() + b.getProtein(),
                a.getCarbs() + b.getCarbs(),
                a.getFats() + b.getFats()
        );
    }
}