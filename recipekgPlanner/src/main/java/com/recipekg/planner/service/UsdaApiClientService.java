package com.recipekg.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.RecipeCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

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

        // 2. Fetch the macro dictionary from USDA (Now includes Portions!)
        Map<String, Object[]> dataDictionary = fetchMacrosFromUsda(new ArrayList<>(uniqueIds));

        // 3. MAP THE DICTIONARY BACK TO THE RECIPES WITH VOLUMETRIC MATH
        
        for (RecipeCandidate recipe : recipes) {
            Set<String> seenIngredients = new HashSet<>();
            recipe.getIngredients().removeIf(ing -> !seenIngredients.add(buildIngredientKey(ing)));
            double totalCal = 0, totalProt = 0, totalCarbs = 0, totalFat = 0, totalSugar=0, totalSodium=0, totalAddedSugar=0;
            StringBuilder combinedUsdaText = new StringBuilder();

            // Iterate over the ACTUAL INGREDIENT USES to get quantity and unit!
            for (IngredientUse use : recipe.getIngredients()) {
                if (use.getUsdaUrl() == null || use.getUsdaUrl().isEmpty()) continue;

                try {
                    String cleanId = use.getUsdaUrl().substring(use.getUsdaUrl().lastIndexOf('/') + 1).trim();

                    if (dataDictionary.containsKey(cleanId)) {
                        Object[] usdaData = dataDictionary.get(cleanId);
                        double[] macros = (double[]) usdaData[0];
                        String text = (String) usdaData[1];
                        JsonNode portions = (JsonNode) usdaData[2]; // Grab the portions array

                        // Calculate the Multiplier!
                        double parsedQty = parseQuantity(use.getQuantity());
                        double multiplier = calculateMultiplier(parsedQty, use.getUnit(), portions);

                        // Apply the multiplier to the 100g baseline macros
                        totalCal += macros[0] * multiplier;
                        totalProt += macros[1] * multiplier;
                        totalCarbs += macros[2] * multiplier;
                        totalFat += macros[3] * multiplier;
                        totalSugar += macros[4] * multiplier;
                        totalAddedSugar += macros[5] * multiplier;
                        totalSodium += macros[6] * multiplier;

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
            recipe.setAddedSugar(Math.round(totalAddedSugar));
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

        try {
            String response = restTemplate.postForObject(url, new HttpEntity<>(requestBody, headers), String.class);
            JsonNode root = mapper.readTree(response);

            for (JsonNode food : root) {
                String fdcId = food.path("fdcId").asText();
                JsonNode nutrients = food.path("foodNutrients");
                JsonNode portions = food.path("foodPortions"); // ADDED THIS

                String ingredientsText = food.path("ingredients").asText("");
                if (ingredientsText.isEmpty()) ingredientsText = food.path("description").asText("");
                
                double cal = 0, prot = 0, carbs = 0, fat = 0, sugar = 0, sodium = 0, addedSugar=0;

                for (JsonNode nut : nutrients) {
                    String unitName = nut.path("unitName").asText("");
                    String name = nut.path("name").asText("");
                    if (name.isEmpty()) name = nut.path("nutrientName").asText("");
                    if (name.isEmpty()) name = nut.path("nutrient").path("name").asText("");

                    String number = nut.path("nutrientNumber").asText("");
                    if (number.isEmpty()) number = nut.path("nutrient").path("number").asText("");

                    double amount = nut.path("amount").asDouble(0.0);

                    String nameLower = name.toLowerCase();
                    String unitLower = unitName.toLowerCase();

                    if ("208".equals(number) || (nameLower.contains("energy") && unitLower.equals("kcal"))) {
                        cal = amount;
                    } else if ("268".equals(number) && cal == 0) {
                        cal = amount * 0.239005736; // kJ -> kcal
                    } else if ("203".equals(number) || nameLower.contains("protein")) {
                        prot = amount;
                    } else if ("205".equals(number) || nameLower.contains("carbohydrate") || nameLower.contains("carb")) {
                        carbs = amount;
                    } else if ("204".equals(number) || nameLower.contains("total lipid") || nameLower.contains("fat")) {
                        fat = amount;
                    } else if ("269".equals(number) || nameLower.contains("sugars, total") || nameLower.contains("total sugars")) {
                        sugar = amount;
                    } else if ("539".equals(number) || nameLower.contains("sugars, added")) {
                        addedSugar = amount;
                    } else if ("307".equals(number) || nameLower.contains("sodium")) {
                        sodium = amount;
                    }
                }
                
                // Pass portions array in slot 2
                result.put(fdcId, new Object[]{new double[]{cal, prot, carbs, fat, sugar, addedSugar, sodium}, ingredientsText, portions});
            }
        } catch (Exception e) {
            System.err.println("USDA API Call Failed: " + e.getMessage());
        }
        return result;
    }

    // --- MATH HELPERS ---

    // Inside UsdaApiClientService.java

private static final Map<String, String> UNIT_MAP = Map.ofEntries(
    Map.entry("tablespoon", "tbsp"),
    Map.entry("tablespoons", "tbsp"),
    Map.entry("tbsp", "tbsp"),
    Map.entry("teaspoon", "tsp"),
    Map.entry("teaspoons", "tsp"),
    Map.entry("tsp", "tsp"),
    Map.entry("cup", "cup"),
    Map.entry("cups", "cup"),
    Map.entry("ounce", "oz"),
    Map.entry("ounces", "oz"),
    Map.entry("oz", "oz"),
    Map.entry("ozs", "oz"),
    Map.entry("pound", "lb"),
    Map.entry("pounds", "lb"),
    Map.entry("lb", "lb"),
    Map.entry("lbs", "lb"),
    Map.entry("gram", "g"),
    Map.entry("grams", "g"),
    Map.entry("g", "g"),
    Map.entry("kilogram", "kg"),
    Map.entry("kilograms", "kg"),
    Map.entry("kg", "kg")
);

private String normalizeUnit(String unit) {
    if (unit == null || unit.isBlank()) return "";
    
    // 1. Convert to lowercase and trim
    String cleanUnit = unit.toLowerCase().trim();
    
    // 2. Remove trailing punctuation (e.g., "tbsp." -> "tbsp")
    cleanUnit = cleanUnit.replaceAll("[\\.,]$", "");

    // 3. Normalize simple plurals (e.g., "lbs" -> "lb")
    if (!UNIT_MAP.containsKey(cleanUnit) && cleanUnit.endsWith("s")) {
        cleanUnit = cleanUnit.substring(0, cleanUnit.length() - 1);
    }
    
    // 4. Return the mapped version if it exists, otherwise return the original
    return UNIT_MAP.getOrDefault(cleanUnit, cleanUnit);
}

private double calculateMultiplier(double quantity, String unit, JsonNode portions) {
    double safeQty = quantity <= 0 ? 1.0 : quantity;

    if (unit == null || unit.isBlank()) {
        return resolvePortionMultiplier("", safeQty, portions);
    }

    // Use the new normalization logic here!
    String normalizedLocalUnit = normalizeUnit(unit);

    if ("g".equals(normalizedLocalUnit)) return safeQty / 100.0;
    if ("kg".equals(normalizedLocalUnit)) return (safeQty * 1000.0) / 100.0;
    if ("oz".equals(normalizedLocalUnit)) return (safeQty * 28.349523125) / 100.0;
    if ("lb".equals(normalizedLocalUnit)) return (safeQty * 453.59237) / 100.0;

    double portionMultiplier = resolvePortionMultiplier(normalizedLocalUnit, safeQty, portions);
    if (portionMultiplier > 0) return portionMultiplier;

    return 0.0;
}
private double parseQuantity(String raw) {
    if (raw == null || raw.isBlank()) return 1.0;

    String cleaned = raw.trim().toLowerCase();
    cleaned = cleaned.replaceAll("[^0-9./\\s-]", " ").trim();

    String[] rangeParts = cleaned.split("\\s*-\\s*");
    if (rangeParts.length == 2) {
        double left = parseSingleQuantity(rangeParts[0]);
        double right = parseSingleQuantity(rangeParts[1]);
        if (left > 0 && right > 0) return (left + right) / 2.0;
        return Math.max(left, right);
    }

    double single = parseSingleQuantity(cleaned);
    return single > 0 ? single : 1.0;
}

private double parseSingleQuantity(String value) {
    if (value == null || value.isBlank()) return 0.0;

    String trimmed = value.trim();

    if (trimmed.contains(" ")) {
        String[] parts = trimmed.split("\\s+");
        double total = 0.0;
        for (String part : parts) {
            total += parseSingleQuantity(part);
        }
        return total;
    }

    if (trimmed.contains("/")) {
        String[] frac = trimmed.split("/");
        if (frac.length == 2) {
            double num = safeParseDouble(frac[0]);
            double den = safeParseDouble(frac[1]);
            if (num > 0 && den > 0) return num / den;
        }
        return 0.0;
    }

    return safeParseDouble(trimmed.replaceAll("[^0-9.]", ""));
}

private double safeParseDouble(String value) {
    try {
        if (value == null || value.isBlank()) return 0.0;
        return Double.parseDouble(value);
    } catch (Exception e) {
        return 0.0;
    }
}

private double resolvePortionMultiplier(String normalizedUnit, double quantity, JsonNode portions) {
    if (portions == null || !portions.isArray()) return 0.0;

    for (JsonNode portion : portions) {
        String modifier = portion.path("modifier").asText("").toLowerCase();
        if (modifier.isEmpty()) continue;

        String modifierUnit = normalizeModifier(modifier);
        if (!normalizedUnit.isEmpty()) {
            if (!modifierUnit.isEmpty() && modifierUnit.contains(normalizedUnit)) {
                double gramWeight = portion.path("gramWeight").asDouble(0.0);
                if (gramWeight > 0) return (gramWeight * quantity) / 100.0;
            }
        } else {
            double gramWeight = portion.path("gramWeight").asDouble(0.0);
            if (gramWeight > 0) return (gramWeight * quantity) / 100.0;
        }
    }

    return 0.0;
}

private String normalizeModifier(String modifier) {
    String cleaned = modifier == null ? "" : modifier.toLowerCase();
    cleaned = cleaned.replaceAll("^[0-9./\\s-]+", "");
    cleaned = cleaned.replaceAll("[(),]", " ");
    cleaned = cleaned.replaceAll("\\s+", " ").trim();

    if (cleaned.isEmpty()) return "";

    String[] tokens = cleaned.split("\\s+");
    StringBuilder normalized = new StringBuilder();
    for (String token : tokens) {
        if (!token.isBlank()) {
            if (normalized.length() > 0) normalized.append(" ");
            normalized.append(normalizeUnit(token));
        }
    }
    return normalized.toString().trim();
}

private String buildIngredientKey(IngredientUse use) {
    if (use == null) return "";
    return String.join("|",
            safeString(use.getName()),
            safeString(use.getQuantity()),
            safeString(use.getUnit()),
            safeString(use.getUsdaUrl()));
}

private String safeString(String value) {
    return value == null ? "" : value.trim();
}
}