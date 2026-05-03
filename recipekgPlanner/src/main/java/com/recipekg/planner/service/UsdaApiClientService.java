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

    @Value("${servings.default:4.0}")
    private double defaultServings;

    @Value("${servings.estimated-calories-per-serving:500.0}")
    private double estimatedCaloriesPerServing;

    @Value("${servings.estimated-min:1.0}")
    private double minEstimatedServings;

    @Value("${servings.estimated-max:12.0}")
    private double maxEstimatedServings;

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
        Map<String, UsdaFoodData> dataDictionary = fetchMacrosFromUsda(new ArrayList<>(uniqueIds));

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
                        UsdaFoodData usdaData = dataDictionary.get(cleanId);
                        MacroProfile macros = usdaData.macrosPer100g();

                        if (isBlank(use.getQuantity())) {
                            combinedUsdaText.append(usdaData.description()).append(" ");
                            continue;
                        }

                        // Calculate the Multiplier!
                        double parsedQty = parseQuantity(use.getQuantity());
                        if (parsedQty <= 0) {
                            combinedUsdaText.append(usdaData.description()).append(" ");
                            continue;
                        }
                        double multiplier = calculateMultiplier(parsedQty, use.getUnit(), use.getName(), usdaData.portions());

                        // Apply the multiplier to the 100g baseline macros
                        totalCal += macros.calories() * multiplier;
                        totalProt += macros.protein() * multiplier;
                        totalCarbs += macros.carbs() * multiplier;
                        totalFat += macros.fat() * multiplier;
                        totalSugar += macros.sugar() * multiplier;
                        totalAddedSugar += macros.addedSugar() * multiplier;
                        totalSodium += macros.sodium() * multiplier;

                        combinedUsdaText.append(usdaData.description()).append(" ");
                    }
                } catch (Exception e) {
                    // Ignore mapping errors
                }
            }

            double servings = resolveServings(recipe, totalCal);
            recipe.setServings(servings);

            recipe.setCalories(totalCal / servings);
            recipe.setProtein(totalProt / servings);
            recipe.setCarbs(totalCarbs / servings);
            recipe.setFat(totalFat / servings);
            recipe.setSugar(totalSugar / servings);
            recipe.setAddedSugar(totalAddedSugar / servings);
            recipe.setSodium(totalSodium / servings);
            recipe.setUsdaIngredientText(combinedUsdaText.toString().trim());
        }
    }

    private Map<String, UsdaFoodData> fetchMacrosFromUsda(List<Integer> fdcIds) {
        Map<String, UsdaFoodData> result = new HashMap<>();
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

                result.put(fdcId, new UsdaFoodData(extractMacros(nutrients), ingredientsText, portions));
            }
        } catch (Exception e) {
            System.err.println("USDA API Call Failed: " + e.getMessage());
        }
        return result;
    }

    private MacroProfile extractMacros(JsonNode nutrients) {
        double calKcal = 0, calKj = 0, prot = 0, carbs = 0, fat = 0, sugar = 0, sodium = 0, addedSugar = 0;

        if (nutrients == null || !nutrients.isArray()) {
            return new MacroProfile(0, 0, 0, 0, 0, 0, 0);
        }

        for (JsonNode nut : nutrients) {
            if (!nut.has("amount")) continue;

            String number = nutrientNumber(nut);
            String unitName = nutrientUnit(nut);
            double amount = nut.path("amount").asDouble(0.0);

            switch (number) {
                case "208" -> {
                    if ("kcal".equalsIgnoreCase(unitName)) calKcal = amount;
                }
                case "268" -> {
                    if ("kj".equalsIgnoreCase(unitName)) calKj = amount;
                }
                case "203" -> prot = amount;
                case "205" -> carbs = amount;
                case "204" -> fat = amount;
                case "269" -> sugar = amount;
                case "539" -> addedSugar = amount;
                case "307" -> sodium = amount;
                default -> {
                    // Ignore nutrient group headings and non-macro nutrients.
                }
            }
        }

        double calories = calKcal > 0 ? calKcal : calKj * 0.239005736;
        return new MacroProfile(calories, prot, carbs, fat, sugar, addedSugar, sodium);
    }

    private String nutrientNumber(JsonNode nutrient) {
        String number = nutrient.path("nutrientNumber").asText("");
        if (number.isEmpty()) number = nutrient.path("nutrient").path("number").asText("");
        return number;
    }

    private String nutrientUnit(JsonNode nutrient) {
        String unitName = nutrient.path("unitName").asText("");
        if (unitName.isEmpty()) unitName = nutrient.path("nutrient").path("unitName").asText("");
        return unitName;
    }

    // --- MATH HELPERS ---

    // Inside UsdaApiClientService.java

private static final Map<String, String> UNIT_MAP = Map.ofEntries(
    Map.entry("tablespn", "tbsp"),
    Map.entry("tablesp", "tbsp"),
    Map.entry("tablespoon", "tbsp"),
    Map.entry("tablespoons", "tbsp"),
    Map.entry("tbs", "tbsp"),
    Map.entry("tbsps", "tbsp"),
    Map.entry("tb", "tbsp"),
    Map.entry("tbsp", "tbsp"),
    Map.entry("tbl", "tbsp"),
    Map.entry("tbls", "tbsp"),
    Map.entry("teasp", "tsp"),
    Map.entry("teaspoon", "tsp"),
    Map.entry("teaspoons", "tsp"),
    Map.entry("tsp", "tsp"),
    Map.entry("tsps", "tsp"),
    Map.entry("c", "cup"),
    Map.entry("cup", "cup"),
    Map.entry("cups", "cup"),
    Map.entry("fl oz", "floz"),
    Map.entry("fluid ounce", "floz"),
    Map.entry("fluid ounces", "floz"),
    Map.entry("floz", "floz"),
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
    Map.entry("milliliter", "ml"),
    Map.entry("milliliters", "ml"),
    Map.entry("ml", "ml"),
    Map.entry("liter", "l"),
    Map.entry("liters", "l"),
    Map.entry("litre", "l"),
    Map.entry("litres", "l"),
    Map.entry("l", "l"),
    Map.entry("kilogram", "kg"),
    Map.entry("kilograms", "kg"),
    Map.entry("kg", "kg")
);

private String normalizeUnit(String unit) {
    if (unit == null || unit.isBlank()) return "";
    
    // 1. Convert to lowercase and trim
    String cleanUnit = unit.toLowerCase().trim();
    
    // 2. Remove punctuation around abbreviations (e.g., "tbsp.", "c.", "oz.")
    cleanUnit = cleanUnit.replaceAll("[\\.,]+$", "");
    cleanUnit = cleanUnit.replaceAll("\\s+", " ");

    // 3. Normalize simple plurals (e.g., "lbs" -> "lb")
    if (!UNIT_MAP.containsKey(cleanUnit) && cleanUnit.endsWith("s")) {
        cleanUnit = cleanUnit.substring(0, cleanUnit.length() - 1);
    }
    
    // 4. Return the mapped version if it exists, otherwise return the original
    return UNIT_MAP.getOrDefault(cleanUnit, cleanUnit);
}

private double calculateMultiplier(double quantity, String unit, String ingredientName, JsonNode portions) {
    if (quantity <= 0) return 0.0;
    double safeQty = quantity;

    // Use the new normalization logic here!
    String normalizedLocalUnit = normalizeUnit(unit);

    if ("g".equals(normalizedLocalUnit)) return safeQty / 100.0;
    if ("kg".equals(normalizedLocalUnit)) return (safeQty * 1000.0) / 100.0;
    if ("oz".equals(normalizedLocalUnit)) return (safeQty * 28.349523125) / 100.0;
    if ("lb".equals(normalizedLocalUnit)) return (safeQty * 453.59237) / 100.0;
    if ("floz".equals(normalizedLocalUnit)) return (safeQty * 29.5735295625) / 100.0;
    if ("ml".equals(normalizedLocalUnit)) return safeQty / 100.0;
    if ("l".equals(normalizedLocalUnit)) return (safeQty * 1000.0) / 100.0;

    double portionMultiplier = resolvePortionMultiplier(normalizedLocalUnit, safeQty, ingredientName, portions);
    if (portionMultiplier > 0) return portionMultiplier;

    return 0.0;
}
private double parseQuantity(String raw) {
    if (raw == null || raw.isBlank()) return 0.0;

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
    return single > 0 ? single : 0.0;
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
            if (looksLikeCorruptedWholeNumber(frac[0], frac[1])) {
                return safeParseDouble((frac[0] + frac[1]).replaceAll("[^0-9]", ""));
            }
            double num = safeParseDouble(frac[0]);
            double den = safeParseDouble(frac[1]);
            if (num > 0 && den > 0) return num / den;
        }
        return 0.0;
    }

    return safeParseDouble(trimmed.replaceAll("[^0-9.]", ""));
}

private boolean looksLikeCorruptedWholeNumber(String numerator, String denominator) {
    String left = numerator == null ? "" : numerator.trim();
    String right = denominator == null ? "" : denominator.trim();
    if (!left.matches("\\d+") || !right.matches("\\d+")) return false;
    return right.contains("0");
}

private double safeParseDouble(String value) {
    try {
        if (value == null || value.isBlank()) return 0.0;
        return Double.parseDouble(value);
    } catch (Exception e) {
        return 0.0;
    }
}

private double resolvePortionMultiplier(String normalizedUnit, double quantity, String ingredientName, JsonNode portions) {
    if (portions == null || !portions.isArray()) return 0.0;

    PortionMatch best = null;
    boolean hasExplicitUnit = !normalizedUnit.isEmpty();

    for (JsonNode portion : portions) {
        String modifier = portion.path("modifier").asText("").toLowerCase();
        String measureName = portion.path("measureUnit").path("name").asText("");
        String measureAbbreviation = portion.path("measureUnit").path("abbreviation").asText("");

        String normalizedModifier = normalizeModifier(modifier);
        String normalizedMeasureName = normalizeModifier(measureName);
        String normalizedMeasureAbbreviation = normalizeModifier(measureAbbreviation);

        double gramWeight = portion.path("gramWeight").asDouble(0.0);
        if (gramWeight <= 0) continue;

        double portionAmount = portion.path("amount").asDouble(1.0);
        if (portionAmount <= 0) portionAmount = 1.0;
        int sequenceNumber = portion.path("sequenceNumber").asInt(Integer.MAX_VALUE);

        int score = scorePortion(
                normalizedUnit,
                normalizedModifier,
                normalizedMeasureName,
                normalizedMeasureAbbreviation,
                ingredientName,
                portionAmount,
                sequenceNumber
        );

        if (score <= 0) continue;

        double gramsPerRecipeUnit = hasExplicitUnit ? gramWeight / portionAmount : gramWeight;
        PortionMatch candidate = new PortionMatch(score, gramsPerRecipeUnit, sequenceNumber);
        if (best == null
                || candidate.score() > best.score()
                || (candidate.score() == best.score() && candidate.sequenceNumber() < best.sequenceNumber())) {
            best = candidate;
        }
    }

    return best == null ? 0.0 : (best.gramsPerRecipeUnit() * quantity) / 100.0;
}

private int scorePortion(
        String normalizedUnit,
        String normalizedModifier,
        String normalizedMeasureName,
        String normalizedMeasureAbbreviation,
        String ingredientName,
        double portionAmount,
        int sequenceNumber
) {
    String combined = String.join(" ", normalizedModifier, normalizedMeasureName, normalizedMeasureAbbreviation).trim();

    if (!normalizedUnit.isEmpty()) {
        if (containsUnitToken(combined, normalizedUnit)) return 100;
        if (isWholeItemUnit(normalizedUnit)
                && containsAny(combined, "whole", "medium", "large", "small", "piece", "item", "fruit", "fillet", "serving")) {
            return 75;
        }
        return 0;
    }

    if (combined.isBlank()) return 0;

    int score = 0;
    boolean slicedOrPrepared = containsAny(combined, "slice", "sliced", "ring", "rings", "chopped", "diced", "minced");
    boolean measured = containsAny(combined, "cup", "tbsp", "tsp", "floz", "oz", "g", "kg", "ml", "l");
    boolean ingredientMentionsCount = ingredientMentionsAny(ingredientName,
            "slice", "piece", "clove", "cube", "stalk", "kernel", "sprig", "leaf", "bunch");

    if (containsAny(combined, "serving", "container", "package", "packet", "can", "bottle", "jar")) score += 85;
    if (containsAny(combined, "whole", "fruit", "item")) score += 80;
    if (containsAny(combined, "piece")) score += ingredientMentionsCount ? 75 : 35;
    if (!slicedOrPrepared && !measured && containsAny(combined, "medium")) score += 78;
    if (!slicedOrPrepared && !measured && containsAny(combined, "large")) score += 72;
    if (!slicedOrPrepared && !measured && containsAny(combined, "small")) score += 65;
    if (containsAny(combined, "clove", "cube", "stalk", "kernel", "sprig", "leaf", "bunch")) score += ingredientMentionsCount ? 75 : 35;
    if (slicedOrPrepared) score += ingredientMentionsCount ? 70 : 35;
    if (containsIngredientToken(combined, ingredientName)) score += 30;

    // With no local unit, quantity means "number of USDA portions".
    // A 12 fl oz beer portion is a plausible portion; a 1 fl oz portion is usually only a sub-unit.
    if (containsAny(combined, "cup")) score += slicedOrPrepared ? 35 : 45;
    if (containsAny(combined, "tbsp", "tsp")) score += 30;
    if (containsAny(combined, "floz", "oz", "g", "ml")) score += portionAmount > 1.0 ? 40 : 15;
    if (containsAny(combined, "lb", "kg", "l")) score += portionAmount > 1.0 ? 25 : 5;
    if (sequenceNumber != Integer.MAX_VALUE) score += Math.max(0, 10 - sequenceNumber);

    return score;
}

private boolean containsUnitToken(String text, String unit) {
    if (text == null || text.isBlank() || unit == null || unit.isBlank()) return false;
    return Arrays.asList(text.split("\\s+")).contains(unit);
}

private boolean containsAny(String text, String... tokens) {
    if (text == null || text.isBlank()) return false;
    for (String token : tokens) {
        if (containsUnitToken(text, token)) return true;
    }
    return false;
}

private boolean isWholeItemUnit(String unit) {
    return containsUnitToken("whole medium large small piece item fruit fillet", unit);
}

private boolean containsIngredientToken(String text, String ingredientName) {
    if (text == null || text.isBlank() || ingredientName == null || ingredientName.isBlank()) return false;

    String cleanedName = ingredientName.toLowerCase().replaceAll("[^a-z\\s]", " ");
    for (String token : cleanedName.split("\\s+")) {
        String normalizedToken = singularize(token);
        if (normalizedToken.length() >= 4 && containsUnitToken(text, normalizedToken)) {
            return true;
        }
    }

    return false;
}

private boolean ingredientMentionsAny(String ingredientName, String... tokens) {
    if (ingredientName == null || ingredientName.isBlank()) return false;

    String cleanedName = ingredientName.toLowerCase().replaceAll("[^a-z\\s]", " ");
    Set<String> ingredientTokens = new HashSet<>();
    for (String token : cleanedName.split("\\s+")) {
        if (!token.isBlank()) {
            ingredientTokens.add(singularize(token));
        }
    }

    for (String token : tokens) {
        if (ingredientTokens.contains(singularize(token))) {
            return true;
        }
    }

    return false;
}

private String singularize(String token) {
    if (token == null) return "";
    String cleaned = token.trim().toLowerCase();
    if (cleaned.length() > 3 && cleaned.endsWith("ies")) {
        return cleaned.substring(0, cleaned.length() - 3) + "y";
    }
    if (cleaned.length() > 3 && cleaned.endsWith("es")) {
        return cleaned.substring(0, cleaned.length() - 2);
    }
    if (cleaned.length() > 3 && cleaned.endsWith("s")) {
        return cleaned.substring(0, cleaned.length() - 1);
    }
    return cleaned;
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

private boolean isBlank(String value) {
    return value == null || value.isBlank();
}

private double resolveServings(RecipeCandidate recipe, double totalCalories) {
    Double servings = recipe.getServings();
    if (servings != null && servings > 0) {
        return servings;
    }

    double estimated = estimateServingsFromCalories(totalCalories);
    if (estimated > 0) {
        return estimated;
    }

    return defaultServings > 0 ? defaultServings : 4.0;
}

private double estimateServingsFromCalories(double totalCalories) {
    if (totalCalories <= 0 || estimatedCaloriesPerServing <= 0) {
        return 0.0;
    }

    double rawEstimate = totalCalories / estimatedCaloriesPerServing;
    double min = minEstimatedServings > 0 ? minEstimatedServings : 1.0;
    double max = maxEstimatedServings >= min ? maxEstimatedServings : 12.0;

    return Math.max(min, Math.min(max, rawEstimate));
}

private record UsdaFoodData(MacroProfile macrosPer100g, String description, JsonNode portions) {}

private record MacroProfile(
        double calories,
        double protein,
        double carbs,
        double fat,
        double sugar,
        double addedSugar,
        double sodium
) {}

private record PortionMatch(int score, double gramsPerRecipeUnit, int sequenceNumber) {}
}
