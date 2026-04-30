package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.GraphDbRepository;
import com.recipekg.planner.response.PantryResponse;
import com.recipekg.planner.service.FoodScientistService;
import com.recipekg.planner.service.UsdaApiClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final MedicalAgentService medicalAgent;
    private final NutritionAgentService nutritionAgent;
    private final FitnessAgentService fitnessAgent;
    private final FoodScientistService foodScientist;
    private final PlanComposerService composer;
    private final GraphDbRepository graphDbRepository;
    private final UsdaApiClientService usdaApiClient;


    public PantryResponse generateFullPlan(UserProfile profile) {


        MedicalManifest medical = medicalAgent.generateMedicalAdvice(profile);


        PantryResponse pantryResponse = foodScientist.fetchSafePantry(profile, medical);


        usdaApiClient.populateMacros(pantryResponse.getRecipes());


        if ("CONSTRAINED".equalsIgnoreCase(medical.status()) && medical.constraints() != null) {
            List<RecipeCandidate> capped =
            foodScientist.filterByNutrientCaps(
                    pantryResponse.getRecipes(),
                    medical.constraints().nutrientCaps()
            );

            List<RecipeCandidate> ultraSafeRecipes =
            applyTierTwoSafetyAndCleanup(capped, medical.constraints().hardExclusions());

            pantryResponse = new PantryResponse(
                pantryResponse.getSparqlQuery(),
                ultraSafeRecipes,
                medical
            );
        }

        return pantryResponse;
    }


    private List<RecipeCandidate> applyTierTwoSafetyAndCleanup(List<RecipeCandidate> candidates, List<String> unrolledKeywords) {

        if (unrolledKeywords == null || unrolledKeywords.isEmpty()) {
            return candidates;
        }


        String regexPattern = unrolledKeywords.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining("|"));

        return candidates.stream()

                .filter(recipe -> recipe.getCalories() > 5.0)

                .filter(recipe -> {
                    if (recipe.getUsdaIngredientText() == null) return true;
                    String text = recipe.getUsdaIngredientText().toLowerCase();

                    return !text.matches(".*(" + regexPattern + ").*");
                })
                .collect(Collectors.toList());
    }
}