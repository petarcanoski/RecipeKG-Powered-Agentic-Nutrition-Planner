package com.recipekg.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.dto.IngredientDTO;
import com.recipekg.planner.dto.MealDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SparqlResultParserService {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<MealDTO> parseMeals(String json) {

        try {

            JsonNode root = mapper.readTree(json);
            JsonNode bindings = root.get("results").get("bindings");

            Map<String, MealDTO> meals = new HashMap<>();

            if (bindings == null || !bindings.isArray()) {
                System.out.println("KG ERROR → no bindings");
                return List.of();
            }

            for (JsonNode row : bindings) {

                String mealUri = row.get("meal").get("value").asText();

                String ingredient = "";
                if (row.has("name"))
                    ingredient = row.get("name").get("value").asText();

                String qty = "";
                if (row.has("qty"))
                    qty = row.get("qty").get("value").asText();

                String unit = "";
                if (row.has("unit"))
                    unit = row.get("unit").get("value").asText();

                MealDTO meal = meals.get(mealUri);

                if (meal == null) {
                    meal = new MealDTO();
                    meal.setMealUri(mealUri);
                    meal.setIngredients(new ArrayList<>());
                    meals.put(mealUri, meal);
                }

                meal.getIngredients().add(
                        new IngredientDTO(ingredient, qty, unit)
                );
            }

            return new ArrayList<>(meals.values());

        } catch (Exception e) {
            throw new RuntimeException("SPARQL parse error", e);
        }
    }
}