package com.recipekg.planner.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.RecipeCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UsdaApiClientService {

    @Value("${usda.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public void populateMacros(List<RecipeCandidate> recipes) {
        Set<Integer> uniqueIds = new HashSet<>();

        // 1. EXTRACT THE IDS FROM THE URLs
        for (RecipeCandidate recipe : recipes) {
            for (String rawUrl : recipe.getUsdaIngredientIds()) {
                try {
                    // Grab everything after the final slash (e.g., "173161")
                    String cleanId = rawUrl.substring(rawUrl.lastIndexOf('/') + 1).trim();
                    uniqueIds.add(Integer.parseInt(cleanId));
                } catch (Exception e) {
                    // Silently ignore malformed strings
                }
            }
        }

        if (uniqueIds.isEmpty()) {
            System.out.println("No valid USDA IDs found to fetch.");
            return;
        }

        // 2. Fetch the macro dictionary from USDA
        Map<String, Object[]> dataDictionary = fetchMacrosFromUsda(new ArrayList<>(uniqueIds));

        // 3. MAP THE DICTIONARY BACK TO THE RECIPES
        for (RecipeCandidate recipe : recipes) {
            double totalCal = 0, totalProt = 0, totalCarbs = 0, totalFat = 0, totalSugar=0,totalSodium=0;
            StringBuilder combinedUsdaText = new StringBuilder();

            for (String rawUrl : recipe.getUsdaIngredientIds()) {
            try {
                String cleanId = rawUrl.substring(rawUrl.lastIndexOf('/') + 1).trim();

                if (dataDictionary.containsKey(cleanId)) {
                    Object[] usdaData = dataDictionary.get(cleanId);
                    double[] macros = (double[]) usdaData[0];
                    String text = (String) usdaData[1];

                    totalCal += macros[0];
                    totalProt += macros[1];
                    totalCarbs += macros[2];
                    totalFat += macros[3];
                    totalSugar += macros[4];
                    totalSodium += macros[5];

                    combinedUsdaText.append(text).append(" ");
                }
            } catch (Exception e) {
                // Ignore mapping errors
            }
        }


            recipe.setCalories(Math.round(totalCal));
            recipe.setProtein(Math.round(totalProt));
            recipe.setCarbs(Math.round(totalCarbs));
            recipe.setFat(Math.round(totalFat));
            recipe.setSugar(Math.round(totalSugar));
            recipe.setSodium(Math.round(totalSodium));
            recipe.setUsdaIngredientText(combinedUsdaText.toString().trim());
        }
    }

    private Map<String, Object[]> fetchMacrosFromUsda(List<Integer> fdcIds) {
        Map<String, Object[]> result = new HashMap<>();
        String url = "https://api.nal.usda.gov/fdc/v1/foods?api_key=" + apiKey;

        Map<String, Object> requestBody = Map.of(
            "fdcIds", fdcIds,
            "format", "full"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

         try {
        String response = restTemplate.postForObject(url, new HttpEntity<>(requestBody, headers), String.class);
        JsonNode root = mapper.readTree(response);

        for (JsonNode food : root) {
            String fdcId = food.path("fdcId").asText();
            JsonNode nutrients = food.path("foodNutrients");

            String ingredientsText = food.path("ingredients").asText("");
            if (ingredientsText.isEmpty()) {
                ingredientsText = food.path("description").asText("");
            }

            double cal = 0, prot = 0, carbs = 0, fat = 0, sugar = 0, sodium = 0;

            for (JsonNode nut : nutrients) {
                String name = nut.path("name").asText("");
                if (name.isEmpty()) name = nut.path("nutrientName").asText("");
                if (name.isEmpty()) name = nut.path("nutrient").path("name").asText("");

                String number = nut.path("nutrientNumber").asText("");
                if (number.isEmpty()) number = nut.path("nutrient").path("number").asText("");

                double amount = nut.path("amount").asDouble(0.0);

                if (name.equalsIgnoreCase("Energy")) cal = amount;
                else if (name.equalsIgnoreCase("Protein")) prot = amount;
                else if (name.equalsIgnoreCase("Carbohydrate, by difference")) carbs = amount;
                else if (name.equalsIgnoreCase("Total lipid (fat)")) fat = amount;
                else if (name.equalsIgnoreCase("Total Sugars")
                        || name.toLowerCase().contains("sugars, total")
                        || "269".equals(number)) sugar = amount;
                else if (name.equalsIgnoreCase("Sodium, Na")) sodium = amount;
            }

            result.put(fdcId, new Object[]{new double[]{cal, prot, carbs, fat, sugar, sodium}, ingredientsText});
        }
    } catch (Exception e) {
        System.err.println("USDA API Call Failed: " + e.getMessage());
    }
    return result;
    }
}
