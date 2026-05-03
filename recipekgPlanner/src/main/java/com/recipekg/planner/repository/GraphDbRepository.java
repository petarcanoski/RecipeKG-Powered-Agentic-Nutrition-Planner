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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphDbRepository {

    private final Repository db;


    public GraphDbRepository(Repository db) {
        this.db = db;
    }

    public List<RecipeCandidate> executeSparql(String sparqlQuery) {
    Map<String, RecipeCandidate> byRecipe = new LinkedHashMap<>();

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

                String servingsRaw = row.hasBinding("servings") ? row.getValue("servings").stringValue() : "";
                Double servings = parseServings(servingsRaw);
                if (servings != null && (candidate.getServings() == null || candidate.getServings() <= 0)) {
                    candidate.setServings(servings);
                }

                String ingNameUri = row.hasBinding("ingName") ? row.getValue("ingName").stringValue() : "";
                String ingLabel = row.hasBinding("ingLabel") ? row.getValue("ingLabel").stringValue() : "";
                String qty = row.hasBinding("qty") ? row.getValue("qty").stringValue() : "";
                String unit = row.hasBinding("unit") ? row.getValue("unit").stringValue() : "";
                String usdaUrl = row.hasBinding("usdaUrl") ? row.getValue("usdaUrl").stringValue() : "";

                if (!ingNameUri.isEmpty()) {
                    String name = !ingLabel.isEmpty() ? ingLabel : ingNameUri;
                    candidate.getIngredients().add(new IngredientUse(name, qty, unit, usdaUrl));
                }

                if (usdaUrl != null && !usdaUrl.isBlank()
                        && !candidate.getUsdaIngredientIds().contains(usdaUrl)) {
                    candidate.getUsdaIngredientIds().add(usdaUrl);
                }
            }
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to execute SPARQL query via RDF4J", e);
    }

    return new ArrayList<>(byRecipe.values());
}

private Double parseServings(String raw) {
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
}