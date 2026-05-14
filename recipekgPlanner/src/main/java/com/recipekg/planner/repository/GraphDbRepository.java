package com.recipekg.planner.repository;

import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.RecipeCandidate;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GraphDbRepository {

    private final Repository db;


    public GraphDbRepository(Repository db) {
        this.db = db;
    }

    public List<RecipeCandidate> executeSparql(String sparqlQuery) {
    Map<String, RecipeCandidate> byRecipe = new LinkedHashMap<>();
    Map<String, Map<String, IngredientAccumulator>> ingredientsByRecipe = new LinkedHashMap<>();

    try (RepositoryConnection conn = db.getConnection()) {
        TupleQuery query = conn.prepareTupleQuery(sparqlQuery);

        try (TupleQueryResult result = query.evaluate()) {
            while (result.hasNext()) {
                BindingSet row = result.next();

                String uri = row.getValue("recipe").stringValue();
                String label = row.getValue("recipeLabel").stringValue();

                RecipeCandidate candidate = byRecipe.get(uri);
                if (candidate == null) {
                    candidate = new RecipeCandidate(uri, label, new ArrayList<>());
                    byRecipe.put(uri, candidate);
                }

                String ingNameUri = row.hasBinding("ingName") ? row.getValue("ingName").stringValue() : "";
                String ingLabel = row.hasBinding("ingLabel") ? row.getValue("ingLabel").stringValue() : "";
                String qty = row.hasBinding("qty") ? row.getValue("qty").stringValue() : "";
                String unit = row.hasBinding("unit") ? row.getValue("unit").stringValue() : "";
                String usdaUrl = row.hasBinding("usdaUrl") ? row.getValue("usdaUrl").stringValue() : "";
                String useUri = row.hasBinding("use") ? row.getValue("use").stringValue() : "";

                if (!ingNameUri.isEmpty()) {
                    String name = !ingLabel.isEmpty() ? ingLabel : ingNameUri;
                    String ingredientKey = !useUri.isBlank() ? useUri : ingNameUri + "|" + usdaUrl;
                    Map<String, IngredientAccumulator> recipeIngredients =
                            ingredientsByRecipe.computeIfAbsent(uri, ignored -> new LinkedHashMap<>());
                    IngredientAccumulator accumulator =
                            recipeIngredients.computeIfAbsent(ingredientKey, ignored -> new IngredientAccumulator(name, usdaUrl));
                    accumulator.add(name, qty, unit, usdaUrl);
                }
            }
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to execute SPARQL query via RDF4J", e);
    }

    attachCollapsedIngredients(byRecipe, ingredientsByRecipe);

    return new ArrayList<>(byRecipe.values());
}

private void attachCollapsedIngredients(Map<String, RecipeCandidate> byRecipe,
                                        Map<String, Map<String, IngredientAccumulator>> ingredientsByRecipe) {
    for (Map.Entry<String, RecipeCandidate> entry : byRecipe.entrySet()) {
        RecipeCandidate candidate = entry.getValue();
        candidate.getIngredients().clear();
        candidate.getUsdaIngredientIds().clear();

        Map<String, IngredientAccumulator> ingredientMap = ingredientsByRecipe.get(entry.getKey());
        if (ingredientMap == null) continue;

        for (IngredientAccumulator accumulator : ingredientMap.values()) {
            IngredientUse ingredient = accumulator.toIngredientUse();
            candidate.getIngredients().add(ingredient);

            String usdaUrl = ingredient.getUsdaUrl();
            if (usdaUrl != null && !usdaUrl.isBlank()
                    && !candidate.getUsdaIngredientIds().contains(usdaUrl)) {
                candidate.getUsdaIngredientIds().add(usdaUrl);
            }
        }
    }
}

private QuantityUnitPair chooseQuantityUnitPair(Set<QuantityUnitPair> pairs) {
    if (pairs == null || pairs.isEmpty()) {
        return new QuantityUnitPair("", "");
    }

    return pairs.stream()
            .min(Comparator
                    .comparingDouble((QuantityUnitPair pair) -> estimatedPairWeight(pair.quantity(), pair.unit()))
                    .thenComparing(pair -> safeString(pair.quantity()))
                    .thenComparing(pair -> safeString(pair.unit())))
            .orElse(new QuantityUnitPair("", ""));
}

private double estimatedPairWeight(String quantity, String unit) {
    if (quantity == null || quantity.isBlank()) return Double.MAX_VALUE;

    Double parsedQuantityValue = parseQuantityValue(quantity);
    double parsedQuantity = parsedQuantityValue == null ? 0.0 : parsedQuantityValue;
    if (parsedQuantity <= 0) return Double.MAX_VALUE;

    return parsedQuantity * unitWeight(unit);
}

private double unitWeight(String unit) {
    String normalized = unit == null ? "" : unit.toLowerCase().trim().replaceAll("[\\.,]+$", "");
    normalized = normalized.replaceAll("\\s+", " ");

    return switch (normalized) {
        case "teaspoon", "teaspoons", "tsp", "tsps" -> 5.0;
        case "tablespoon", "tablespoons", "tbsp", "tbs", "tbsps" -> 15.0;
        case "cup", "cups", "c" -> 240.0;
        case "fl oz", "fluid ounce", "fluid ounces", "floz" -> 30.0;
        case "ounce", "ounces", "oz" -> 28.35;
        case "pound", "pounds", "lb", "lbs" -> 453.59;
        case "gram", "grams", "g" -> 1.0;
        case "kilogram", "kilograms", "kg" -> 1000.0;
        case "milliliter", "milliliters", "ml" -> 1.0;
        case "liter", "liters", "litre", "litres", "l" -> 1000.0;
        default -> 1.0;
    };
}

private String safeString(String value) {
    return value == null ? "" : value.trim();
}

private Double parseQuantityValue(String raw) {
    if (raw == null || raw.isBlank()) return null;

    String cleaned = raw.trim().toLowerCase();
    cleaned = cleaned.replaceAll("\\b(to|or)\\b", "-");
    cleaned = cleaned.replaceAll("[^0-9./\\s-]", " ").trim();
    if (cleaned.isBlank()) return null;

    String[] rangeParts = cleaned.split("\\s*-\\s*");
    if (rangeParts.length == 2) {
        double left = parseSingleQuantity(rangeParts[0]);
        double right = parseSingleQuantity(rangeParts[1]);
        if (left > 0 && right > 0) return (left + right) / 2.0;
        double max = Math.max(left, right);
        return max > 0 ? max : null;
    }

    double single = parseSingleQuantity(cleaned);
    return single > 0 ? single : null;
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

private class IngredientAccumulator {
    private String name;
    private String usdaUrl;
    private final Set<QuantityUnitPair> pairs = new LinkedHashSet<>();

    private IngredientAccumulator(String name, String usdaUrl) {
        this.name = safeString(name);
        this.usdaUrl = safeString(usdaUrl);
    }

    private void add(String name, String quantity, String unit, String usdaUrl) {
        if (this.name.isBlank() && name != null) {
            this.name = safeString(name);
        }
        if (this.usdaUrl.isBlank() && usdaUrl != null) {
            this.usdaUrl = safeString(usdaUrl);
        }
        pairs.add(new QuantityUnitPair(safeString(quantity), safeString(unit)));
    }

    private IngredientUse toIngredientUse() {
        QuantityUnitPair pair = chooseQuantityUnitPair(pairs);
        return new IngredientUse(name, pair.quantity(), pair.unit(), usdaUrl);
    }
}

private record QuantityUnitPair(String quantity, String unit) {}
}
