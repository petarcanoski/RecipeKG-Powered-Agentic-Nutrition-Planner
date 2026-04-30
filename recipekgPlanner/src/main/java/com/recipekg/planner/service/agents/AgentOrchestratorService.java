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

        PantryResponse pantryResponse =
                foodScientist.fetchSafePantry(profile, medical);

        // Fetch macros from usda via api
        usdaApiClient.populateMacros(pantryResponse.getRecipes());



        return pantryResponse;
    }
}