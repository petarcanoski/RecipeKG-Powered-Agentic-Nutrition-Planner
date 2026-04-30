package com.recipekg.planner.service;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutrientCap;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.GraphDbRepository;
import com.recipekg.planner.response.PantryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FoodScientistService {

    private final GraphDbRepository graphDbRepository;

    public FoodScientistService(GraphDbRepository graphDbRepository) {
        this.graphDbRepository = graphDbRepository;
    }

    private static final String SPARQL_PREFIXES = """
            PREFIX heals: <http://idea.rpi.edu/heals/kb/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX usda: <http://example.org/usda/>
            PREFIX foodon: <http://purl.obolibrary.org/obo/>
            """;

    public PantryResponse fetchSafePantry(UserProfile profile, MedicalManifest manifest) {
        String sparqlQuery="";

        if ("CONSTRAINED".equalsIgnoreCase(manifest.status())) {
            sparqlQuery = buildSafeCandidateQuery(manifest);
        } else {
//            sparqlQuery = buildUnconstrainedQuery(profile);
        }

        // Execute the query
        List<RecipeCandidate> results = graphDbRepository.executeSparql(sparqlQuery);

        return new PantryResponse(sparqlQuery, results,manifest);
    }

    public String buildSafeCandidateQuery(MedicalManifest manifest) {
        StringBuilder query = new StringBuilder();
        query.append(SPARQL_PREFIXES).append("\n");


        query.append("SELECT ?recipe ?recipeLabel (GROUP_CONCAT(DISTINCT ?usdaUrl; separator=\",\") AS ?usdaIds) \n");
        query.append("WHERE {\n");


        query.append("  {\n");
        query.append("    SELECT ?recipe ?recipeLabel WHERE {\n");
        query.append("      ?recipe a heals:recipe ; rdfs:label ?recipeLabel .\n");

        if (manifest.constraints() != null) {
            injectKeywordExclusions(query, manifest.constraints().hardExclusions());
        }

        query.append("    }\n");
        query.append("    ORDER BY UUID()\n"); // Shuffle the safe recipes
        query.append("    LIMIT 100\n");
        query.append("  }\n\n");


        query.append("  ?recipe heals:uses/heals:ing_name ?name .\n");
        query.append("  ?name owl:equivalentClass ?usdaItem .\n");


        query.append("  FILTER(CONTAINS(STR(?usdaItem), \"fdc.nal.usda.gov\")) \n");


        query.append("  BIND(STR(?usdaItem) AS ?usdaUrl) \n");

        query.append("}\n");
        query.append("GROUP BY ?recipe ?recipeLabel\n");

        return query.toString();
    }
    private void injectKeywordExclusions(StringBuilder query, List<String> exclusions) {
        if (exclusions == null || exclusions.isEmpty()) return;


        String regexPattern = exclusions.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining("|"));

        query.append("      # --- TIER 1: FAST LOCAL INGREDIENT EXCLUSION ---\n");
        query.append("      FILTER NOT EXISTS {\n");
        // We look strictly at the text label of the ingredient. No tree climbing!
        query.append("        ?recipe heals:uses/heals:ing_name/rdfs:label ?ingLabel .\n");
        query.append("        FILTER(REGEX(STR(?ingLabel), \"").append(regexPattern).append("\", \"i\"))\n");
        query.append("      }\n\n");
    }


    public List<RecipeCandidate> filterByNutrientCaps(List<RecipeCandidate> recipes, List<NutrientCap> caps) {
    if (caps == null || caps.isEmpty()) return recipes;

    return recipes.stream()
            .filter(recipe -> withinCaps(recipe, caps))
            .collect(Collectors.toList());
}

private boolean withinCaps(RecipeCandidate recipe, List<NutrientCap> caps) {
    for (NutrientCap cap : caps) {
        if (cap == null || cap.nutrient() == null) continue;

        String key = cap.nutrient().trim().toLowerCase();
        double value = resolveNutrientValue(recipe, key);

        if (Double.isNaN(value)) continue;

        double normalized = normalizeUnit(value, key, cap.unit());

        if (normalized > cap.maxValue()) return false;
    }
    return true;
}

private double resolveNutrientValue(RecipeCandidate recipe, String key) {
    if (key.equals("carbohydrate, by difference")) return recipe.getCarbs();
    if (key.equals("sodium, na") || key.equals("sodium")) return recipe.getSodium();
    if (key.contains("sugars, total")) return recipe.getSugar();
    if (key.equals("energy")) return recipe.getCalories();
    if (key.equals("protein")) return recipe.getProtein();
    if (key.equals("total lipid (fat)") || key.equals("fat")) return recipe.getFat();
    return Double.NaN;
}

private double normalizeUnit(double value, String nutrientKey, String unit) {
    if (unit == null) return value;

    String u = unit.trim().toLowerCase();
    boolean baseIsMg = nutrientKey.startsWith("sodium");

    if (baseIsMg && u.equals("g")) return value / 1000.0;
    if (!baseIsMg && u.equals("mg")) return value * 1000.0;

    return value;
}


    private void injectUsdaNutrientCaps(StringBuilder query, List<NutrientCap> caps) {
        if (caps == null || caps.isEmpty()) return;

        query.append("  # --- USDA NUTRIENT TRACKING ---\n");
        query.append("  ?name owl:equivalentClass ?usdaItem .\n");

        int counter = 1;
        for (NutrientCap cap : caps) {
            String nutrientName = cap.nutrient();

            // Generate unique variable names for each nutrient requested
            String nutNode = "?nutNode" + counter;
            String nutVal = "?nutVal" + counter;

            query.append("  OPTIONAL {\n");
            query.append("    ?usdaItem usda:hasNutrient ").append(nutNode).append(" .\n");
            query.append("    ").append(nutNode).append(" usda:nutrientName \"").append(nutrientName).append("\" ;\n");
            query.append("              usda:nutrientValue ").append(nutVal).append(" .\n");
            query.append("  }\n");
            counter++;
        }
        query.append("\n");
    }


    // Query template:

//    PREFIX heals: <http://idea.rpi.edu/heals/kb/>
//    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
//    PREFIX owl: <http://www.w3.org/2002/07/owl#>
//    PREFIX usda: <http://example.org/usda/>
//    PREFIX foodon: <http://purl.obolibrary.org/obo/>
//
//    SELECT ?recipe ?recipeLabel
//    WHERE {
//  ?recipe a heals:recipe ; rdfs:label ?recipeLabel .
//                ?recipe heals:uses ?use .
//                ?use heals:ing_name ?name .
//
//  # --- FOODON HARD EXCLUSIONS ---
//  # This block physically removes any recipe containing a subclass of the forbidden items.
//                FILTER NOT EXISTS {
//    ?name owl:equivalentClass ?foodOnClass .
//                    ?foodOnClass rdfs:subClassOf* ?parentClass .
//                    ?parentClass rdfs:label ?foLabel .
//                    FILTER(LCASE(STR(?foLabel)) IN ("dairy product", "added sugar", "refined grain product", "sugar-sweetened beverage"))
//        }
//
//  # --- USDA NUTRIENT TRACKING ---
//  # This block fetches the exact numerical values for the exact nutrients the doctor cares about.
//                ?name owl:equivalentClass ?usdaItem .
//                OPTIONAL {
//    ?usdaItem usda:hasNutrient ?nutNode1 .
//                    ?nutNode1 usda:nutrientName "Carbohydrate, by difference" ;
//            usda:nutrientValue ?nutVal1 .
//        }
//        OPTIONAL {
//    ?usdaItem usda:hasNutrient ?nutNode2 .
//                    ?nutNode2 usda:nutrientName "Sodium" ;
//            usda:nutrientValue ?nutVal2 .
//        }
//    }
//    GROUP BY ?recipe ?recipeLabel
//    LIMIT 50

    public List<RecipeCandidate>buildUnconstrainedQuery(){
        return null;
    }
}