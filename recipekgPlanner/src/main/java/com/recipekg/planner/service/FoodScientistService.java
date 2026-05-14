package com.recipekg.planner.service;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.model.NutrientCap;
import com.recipekg.planner.repository.GraphDbRepository;
import com.recipekg.planner.response.PantryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FoodScientistService {

    private final GraphDbRepository graphDbRepository;
    private final UsdaApiClientService usdaApiClientService;
    private final PerformanceScoringService performanceScoringService;

    @Value("${recipe.candidates.pool-limit:500}")
    private int candidatePoolLimit;

    @Value("${recipe.candidates.enrichment-limit:100}")
    private int enrichmentLimit;

    public FoodScientistService(GraphDbRepository graphDbRepository,
                                UsdaApiClientService usdaApiClientService,
                                PerformanceScoringService performanceScoringService) {
        this.graphDbRepository = graphDbRepository;
        this.usdaApiClientService = usdaApiClientService;
        this.performanceScoringService = performanceScoringService;
    }

    private static final String SPARQL_PREFIXES = """
            PREFIX heals: <http://idea.rpi.edu/heals/kb/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX usda: <http://example.org/usda/>
            PREFIX foodon: <http://purl.obolibrary.org/obo/>
            PREFIX oboInOwl: <http://www.geneontology.org/formats/oboInOwl#>
            """;

    // public PantryResponse fetchSafePantry(UserProfile profile, MedicalManifest manifest) {
    //     return fetchSafePantry(profile, manifest, null);
    // }

    public PantryResponse fetchSafePantry(UserProfile profile, MedicalManifest manifest, PerformanceManifest performanceManifest) {
        String sparqlQuery = buildSafeCandidateQuery(manifest);

        // --- TIER 0 & 1: Fetch a broad, medically filtered candidate pool from GraphDB ---
        List<RecipeCandidate> results = graphDbRepository.executeSparql(sparqlQuery);

        // --- TIER 1b: Cheap local ranking before expensive USDA macro enrichment ---
        results = preRankCandidates(results, performanceManifest).stream()
                .limit(Math.max(1, enrichmentLimit))
                .collect(Collectors.toList());

        // --- TIER 2a: Volumetric Macro Calculation ---
        // Populates per-serving macros using USDA per-100g data and deterministic serving estimates.
        usdaApiClientService.populateMacros(results);

        // --- TIER 2b: Generic Nutrient Cap Filtering ---
        // Dynamically enforces every nutrient limit in the manifest against per-serving macros.
        if (manifest != null && "CONSTRAINED".equalsIgnoreCase(manifest.status())) {
            results = enforceMedicalCaps(results, manifest);
        }

        // --- TIER 3: Performance scoring. Medical safety remains the hard gate. ---
        results = performanceScoringService.scoreAndRank(results, performanceManifest);
        results = keepDiverseLabels(results);

        return new PantryResponse(sparqlQuery, results, manifest, performanceManifest);
    }

   public String buildSafeCandidateQuery(MedicalManifest manifest) {
    StringBuilder query = new StringBuilder();
    query.append(SPARQL_PREFIXES).append("\n");

    query.append("SELECT DISTINCT ?recipe ?recipeLabel ?use ?ingName ?ingLabel ?qty ?unit (SAMPLE(?usdaUrl) AS ?usdaUrl) \n");
    query.append("WHERE {\n");

    query.append("  {\n");
    query.append("    SELECT ?recipe ?recipeLabel (COUNT(DISTINCT ?usdaItem) AS ?mappedCount) WHERE {\n");
    query.append("      ?recipe a heals:recipe ; rdfs:label ?recipeLabel .\n");

    if (manifest != null && manifest.constraints() != null) {
        injectKeywordExclusions(query, manifest.constraints().hardExclusions());
        injectSemanticExclusions(query, manifest.constraints().hardExclusions());
    }

    query.append("      OPTIONAL {\n");
    query.append("        ?recipe heals:uses/heals:ing_name ?candidateIng .\n");
    query.append("        ?candidateIng owl:equivalentClass ?usdaItem .\n");
    query.append("        FILTER(CONTAINS(STR(?usdaItem), \"fdc.nal.usda.gov\"))\n");
    query.append("      }\n");
    query.append("    }\n");
    query.append("    GROUP BY ?recipe ?recipeLabel\n");
    query.append("    ORDER BY DESC(?mappedCount) (LCASE(STR(?recipeLabel)))\n");
    query.append("    LIMIT ").append(Math.max(1, candidatePoolLimit)).append("\n");
    query.append("  }\n\n");

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

private List<RecipeCandidate> preRankCandidates(List<RecipeCandidate> candidates, PerformanceManifest performanceManifest) {
    if (candidates == null || candidates.isEmpty()) return candidates;

    return candidates.stream()
            .sorted(Comparator.comparingDouble((RecipeCandidate recipe) -> cheapCandidateScore(recipe, performanceManifest)).reversed())
            .collect(Collectors.toList());
}

private double cheapCandidateScore(RecipeCandidate recipe, PerformanceManifest performanceManifest) {
    double score = 0.0;
    score += recipe.getUsdaIngredientIds().size() * 8.0;

    int ingredientCount = recipe.getIngredients().size();
    if (ingredientCount > 0) score += Math.min(ingredientCount, 12);

    for (var ingredient : recipe.getIngredients()) {
        boolean hasUsda = ingredient.getUsdaUrl() != null && !ingredient.getUsdaUrl().isBlank();
        boolean hasQuantity = ingredient.getQuantity() != null && !ingredient.getQuantity().isBlank();
        boolean hasUnit = ingredient.getUnit() != null && !ingredient.getUnit().isBlank();

        if (hasUsda) score += 2.0;
        if (hasQuantity && ingredient.getQuantity().matches(".*\\d.*")) score += 3.0;
        if (hasQuantity && hasUnit) score += 1.0;
        if (!hasQuantity) score -= 1.0;
        if (hasQuantity && !hasUnit) score -= 0.5;
    }

    score += goalHintScore(recipe, performanceManifest);
    return score;
}

private double goalHintScore(RecipeCandidate recipe, PerformanceManifest performanceManifest) {
    if (performanceManifest == null || performanceManifest.goalStatus() == null) return 0.0;

    String goal = performanceManifest.goalStatus().toUpperCase(Locale.ROOT);
    String text = (recipe.getLabel() + " " + recipe.getIngredients().stream()
            .map(ingredient -> ingredient.getName() == null ? "" : ingredient.getName())
            .collect(Collectors.joining(" "))).toLowerCase(Locale.ROOT);

    if ("HYPERTROPHY".equals(goal)) {
        return containsAnyLoose(text, "chicken", "beef", "turkey", "salmon", "tuna", "shrimp", "egg", "bean", "lentil") ? 8.0 : 0.0;
    }
    if ("FAT_LOSS".equals(goal)) {
        return containsAnyLoose(text, "salad", "chicken", "fish", "tuna", "vegetable", "bean", "lentil") ? 6.0 : 0.0;
    }
    if ("ENDURANCE".equals(goal)) {
        return containsAnyLoose(text, "rice", "potato", "pasta", "oat", "bean", "lentil", "fruit") ? 6.0 : 0.0;
    }

    return 0.0;
}

private boolean containsAnyLoose(String text, String... terms) {
    if (text == null || text.isBlank()) return false;
    for (String term : terms) {
        if (text.contains(term)) return true;
    }
    return false;
}

private List<RecipeCandidate> keepDiverseLabels(List<RecipeCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) return candidates;

    Map<String, RecipeCandidate> byLabel = new LinkedHashMap<>();
    for (RecipeCandidate candidate : candidates) {
        byLabel.putIfAbsent(normalizeLabel(candidate.getLabel()), candidate);
    }
    return byLabel.values().stream().toList();
}

private String normalizeLabel(String label) {
    if (label == null) return "";
    return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
    if (name.contains("sugar")) return recipe.getSugar();
    if (name.contains("fat") || name.contains("lipid")) return recipe.getFat();
    if (name.contains("sodium")) return recipe.getSodium();

    return 0.0; // Default if nutrient isn't tracked in our local model
}

}
