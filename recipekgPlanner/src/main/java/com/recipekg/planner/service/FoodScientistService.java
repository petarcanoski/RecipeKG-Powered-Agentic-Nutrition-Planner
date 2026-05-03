package com.recipekg.planner.service;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.model.NutrientCap;
import com.recipekg.planner.repository.GraphDbRepository;
import com.recipekg.planner.response.PantryResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FoodScientistService {

    private final GraphDbRepository graphDbRepository;
    private final UsdaApiClientService usdaApiClientService;

    public FoodScientistService(GraphDbRepository graphDbRepository, UsdaApiClientService usdaApiClientService) {
        this.graphDbRepository = graphDbRepository;
        this.usdaApiClientService = usdaApiClientService;
    }

    private static final String SPARQL_PREFIXES = """
            PREFIX heals: <http://idea.rpi.edu/heals/kb/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX usda: <http://example.org/usda/>
            PREFIX foodon: <http://purl.obolibrary.org/obo/>
            PREFIX oboInOwl: <http://www.geneontology.org/formats/oboInOwl#>
            """;

    public PantryResponse fetchSafePantry(UserProfile profile, MedicalManifest manifest) {
        String sparqlQuery = buildSafeCandidateQuery(manifest);

        // --- TIER 0 & 1: Fetch 100 raw recipes from GraphDB (Allergen Filtered) ---
        List<RecipeCandidate> results = graphDbRepository.executeSparql(sparqlQuery);

        // --- TIER 2a: Volumetric Macro Calculation ---
        // Populates per-serving macros using USDA per-100g data and deterministic serving estimates.
        usdaApiClientService.populateMacros(results);

        // --- TIER 2b: Generic Nutrient Cap Filtering ---
        // Dynamically enforces every nutrient limit in the manifest against per-serving macros.
        if ("CONSTRAINED".equalsIgnoreCase(manifest.status())) {
            results = enforceMedicalCaps(results, manifest);
        }

    

        return new PantryResponse(sparqlQuery, results, manifest);
    }

   public String buildSafeCandidateQuery(MedicalManifest manifest) {
    StringBuilder query = new StringBuilder();
    query.append(SPARQL_PREFIXES).append("\n");

    query.append("SELECT DISTINCT ?recipe ?recipeLabel ?use ?ingName ?ingLabel ?qty ?unit (SAMPLE(?usdaUrl) AS ?usdaUrl) (SAMPLE(?servings) AS ?servings) \n");
    query.append("WHERE {\n");

    query.append("  {\n");
    query.append("    SELECT DISTINCT ?recipe ?recipeLabel WHERE {\n");
    query.append("      ?recipe a heals:recipe ; rdfs:label ?recipeLabel .\n");

    if (manifest.constraints() != null) {
        injectKeywordExclusions(query, manifest.constraints().hardExclusions());
        injectSemanticExclusions(query, manifest.constraints().hardExclusions());
    }

    query.append("    }\n");
    query.append("    ORDER BY RAND()\n");
    query.append("    LIMIT 50\n");
    query.append("  }\n\n");

    query.append("  OPTIONAL { ?recipe heals:servings ?servings0 }\n");
    query.append("  OPTIONAL { ?recipe heals:servingSize ?servings1 }\n");
    query.append("  OPTIONAL { ?recipe heals:recipeYield ?servings2 }\n");
    query.append("  OPTIONAL { ?recipe heals:yield ?servings3 }\n");
    query.append("  OPTIONAL { ?recipe heals:serves ?servings4 }\n");
    query.append("  OPTIONAL { ?recipe <http://schema.org/recipeYield> ?servings5 }\n");
    query.append("  OPTIONAL { ?recipe <https://schema.org/recipeYield> ?servings6 }\n");
    query.append("  BIND(COALESCE(?servings0, ?servings1, ?servings2, ?servings3, ?servings4, ?servings5, ?servings6) AS ?servings)\n");
    query.append("\n");

    query.append("  ?recipe heals:uses ?use .\n");
    query.append("  ?use heals:ing_name ?ingName .\n");
    query.append("  OPTIONAL { ?ingName rdfs:label ?ingLabel }\n");
    query.append("  OPTIONAL { ?use heals:ing_quantity ?qty }\n");
    query.append("  OPTIONAL { ?use heals:ing_unit ?unit }\n");
    query.append("  OPTIONAL {\n");
    query.append("    ?ingName owl:equivalentClass ?usdaItem .\n");
    query.append("    FILTER(CONTAINS(STR(?usdaItem), \"fdc.nal.usda.gov\")) \n");
    query.append("    BIND(STR(?usdaItem) AS ?usdaUrl) \n");
    query.append("  }\n");

    query.append("}\n");
    query.append("GROUP BY ?recipe ?recipeLabel ?use ?ingName ?ingLabel ?qty ?unit\n");

    return query.toString();
}


    private void injectKeywordExclusions(StringBuilder query, List<String> exclusions) {
    if (exclusions == null || exclusions.isEmpty()) return;

    List<String> sanitizedExclusions = sanitizeExclusions(exclusions);

    String ingredientContains = sanitizedExclusions.stream()
            .map(s -> "CONTAINS(LCASE(STR(?badName)), \"" + s + "\")")
            .collect(Collectors.joining(" || "));

    String labelContains = sanitizedExclusions.stream()
            .map(s -> "CONTAINS(LCASE(STR(?recipeLabel)), \"" + s + "\")")
            .collect(Collectors.joining(" || "));

    if (ingredientContains.isEmpty()) return;

    query.append("      # --- TIER 0: DIRECT INGREDIENT EXCLUSION ---\n");
    query.append("      FILTER(!(").append(labelContains).append("))\n");
    query.append("      FILTER NOT EXISTS {\n");
    query.append("        ?recipe heals:uses/heals:ing_name ?badName .\n");
    query.append("        FILTER(").append(ingredientContains).append(")\n");
    query.append("      }\n\n");
}
   private void injectSemanticExclusions(StringBuilder query, List<String> exclusions) {
    if (exclusions == null || exclusions.isEmpty()) return;

    String termPattern = sanitizeExclusions(exclusions).stream()
        .map(s -> s.replaceAll("([.^$*+?()\\[\\]{}|\\\\])", "\\\\$1"))
        .collect(Collectors.joining("|"));

    if (termPattern.isEmpty()) return;

    String regex = "(^|\\\\W)(" + termPattern + ")(\\\\W|$)";

    query.append("      # --- TIER 1: SEMANTIC FOODON EXCLUSION ---\n");
    query.append("      # Traverses the ontology to remove subclasses of forbidden items\n");
    query.append("      FILTER NOT EXISTS {\n");
    query.append("        ?recipe heals:uses/heals:ing_name ?name .\n");
    query.append("        ?name owl:equivalentClass ?foodOnClass .\n");
    query.append("        FILTER(STRSTARTS(STR(?foodOnClass), \"http://purl.obolibrary.org/obo/FOODON_\"))\n");
    query.append("\n");
    query.append("        ?foodOnClass rdfs:subClassOf* ?parentClass .\n");
    query.append("        FILTER(STRSTARTS(STR(?parentClass), \"http://purl.obolibrary.org/obo/FOODON_\"))\n");
    query.append("\n");
    query.append("        OPTIONAL {\n");
    query.append("          ?parentClass rdfs:label ?foLabel .\n");
    query.append("          FILTER(LANG(?foLabel) = \"\" || LANGMATCHES(LANG(?foLabel), \"en\"))\n");
    query.append("        }\n");
    query.append("        OPTIONAL { ?parentClass oboInOwl:hasSynonym ?foSyn . }\n");
    query.append("\n");
    query.append("        FILTER(\n");
    query.append("          REGEX(LCASE(STR(?foLabel)), \"").append(regex).append("\") ||\n");
    query.append("          REGEX(LCASE(STR(?foSyn)), \"").append(regex).append("\")\n");
    query.append("        )\n");
    query.append("      }\n\n");
    }

private List<String> sanitizeExclusions(List<String> exclusions) {
    return exclusions.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> s.toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\""))
            .distinct()
            .toList();
}


    private List<RecipeCandidate> enforceMedicalCaps(List<RecipeCandidate> candidates, MedicalManifest manifest) {
    if (manifest.constraints() == null || manifest.constraints().nutrientCaps() == null || manifest.constraints().nutrientCaps().isEmpty()) {
        return candidates;
    }

    return candidates.stream()
            .filter(recipe -> isRecipeSafe(recipe, manifest.constraints().nutrientCaps()))
            // Still sorting by a preference, e.g., protein-dense or low-calorie
            .sorted(Comparator.comparingDouble(RecipeCandidate::getCalories))
            .collect(Collectors.toList());
}

/**
 * Validates a single recipe against ALL active medical nutrient caps.
 */
private boolean isRecipeSafe(RecipeCandidate recipe, List<NutrientCap> caps) {
    for (NutrientCap cap : caps) {
        double servingValue = getMacroValueByName(recipe, cap.nutrient());

        // If even ONE nutrient exceeds the limit, the recipe is discarded
        if (servingValue > cap.maxValue()) {
            System.out.println("Discarding " + recipe.getLabel() + " due to " + cap.nutrient() + " limit.");
            return false;
        }
    }
    return true;
}

/**
 * Helper to map the Nutrient string name from the Medical Manifest 
 * to the actual data fields in our RecipeCandidate.
 */
private double getMacroValueByName(RecipeCandidate recipe, String nutrientName) {
    if (nutrientName == null || nutrientName.isBlank()) return 0.0;

    String name = nutrientName.toLowerCase();

    if (name.contains("energy") || name.contains("calories")) return recipe.getCalories();
    if (name.contains("protein")) return recipe.getProtein();
    if (name.contains("carbohydrate") || name.contains("carb")) return recipe.getCarbs();
    if (name.contains("added") && name.contains("sugar")) return recipe.getAddedSugar();
    if (name.contains("sugar")) return recipe.getSugar();
    if (name.contains("fat") || name.contains("lipid")) return recipe.getFat();
    if (name.contains("sodium")) return recipe.getSodium();

    return 0.0; // Default if nutrient isn't tracked in our local model
}

}
