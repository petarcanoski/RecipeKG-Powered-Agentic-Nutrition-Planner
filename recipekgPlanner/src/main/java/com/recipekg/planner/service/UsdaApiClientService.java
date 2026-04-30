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
        // 1. Gather all unique USDA IDs and cast them to STRICT INTEGERS
        Set<Integer> uniqueIds = new HashSet<>();
        for (RecipeCandidate recipe : recipes) {
            for (String idString : recipe.getUsdaIngredientIds()) {
                try {
                    // This strips out any accidental spaces or non-numeric graph junk
                    uniqueIds.add(Integer.parseInt(idString.trim()));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid id found");
                }
            }
        }

        if (uniqueIds.isEmpty()) {
            System.out.println("No valid USDA IDs found to fetch.");
            return;
        }

        // 2. Fetch the macro dictionary from USDA in one bulk call
        Map<String, double[]> macroDictionary = fetchMacrosFromUsda(new ArrayList<>(uniqueIds));

        // 3. Sum up the macros for each recipe
        for (RecipeCandidate recipe : recipes) {
            double totalCal = 0, totalProt = 0, totalCarbs = 0, totalFat = 0;

            for (String idString : recipe.getUsdaIngredientIds()) {
                String cleanId = idString.trim(); // Match the trimmed string

                if (macroDictionary.containsKey(cleanId)) {
                    double[] macros = macroDictionary.get(cleanId);
                    totalCal += macros[0];   // Energy
                    totalProt += macros[1];  // Protein
                    totalCarbs += macros[2]; // Carbs
                    totalFat += macros[3];   // Fat
                }
            }

            recipe.setCalories(Math.round(totalCal));
            recipe.setProtein(Math.round(totalProt));
            recipe.setCarbs(Math.round(totalCarbs));
            recipe.setFat(Math.round(totalFat));
        }
    }

    private Map<String, double[]> fetchMacrosFromUsda(List<Integer> fdcIds) {
        Map<String, double[]> result = new HashMap<>();
        String url = "https://api.nal.usda.gov/fdc/v1/foods?api_key=" + apiKey;

        // Send Integers, and use the "abridged" format for a much faster API response
        Map<String, Object> requestBody = Map.of(
                "fdcIds", fdcIds,
                "format", "abridged"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            String response = restTemplate.postForObject(url, request, String.class);
            JsonNode root = mapper.readTree(response);

            // Parse the USDA response format
            for (JsonNode food : root) {
                // We read it back as a string here so it matches the Recipe object's List<String>
                String fdcId = food.path("fdcId").asText();
                JsonNode nutrients = food.path("foodNutrients");

                double cal = 0, prot = 0, carbs = 0, fat = 0;

                for (JsonNode nut : nutrients) {
                    // In the 'abridged' format, the nutrient names are often found like this:
                    String name = nut.path("name").asText("");
                    double amount = nut.path("amount").asDouble(0.0);

                    if (name.equalsIgnoreCase("Energy")) cal = amount;
                    else if (name.equalsIgnoreCase("Protein")) prot = amount;
                    else if (name.equalsIgnoreCase("Carbohydrate, by difference")) carbs = amount;
                    else if (name.equalsIgnoreCase("Total lipid (fat)")) fat = amount;
                }

                result.put(fdcId, new double[]{cal, prot, carbs, fat});
            }
        } catch (Exception e) {
            System.err.println("USDA API Call Failed: " + e.getMessage());
        }
        return result;
    }
}
