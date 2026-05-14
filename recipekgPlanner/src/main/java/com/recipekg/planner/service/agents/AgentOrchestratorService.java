package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.response.PantryResponse;
import com.recipekg.planner.service.FoodScientistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final MedicalAgentService medicalAgent;
    private final PerformanceAgentService performanceAgent;
    private final FoodScientistService foodScientist;
    private final NutritionistAgentService nutritionistAgent;


    public PantryResponse generateFullPlan(UserProfile profile) {


        MedicalManifest medical = medicalAgent.generateMedicalAdvice(profile);
        PerformanceManifest performance = performanceAgent.generatePerformanceManifest(profile, medical);


        PantryResponse pantryResponse = foodScientist.fetchSafePantry(profile, medical, performance);
        NutritionPlan nutritionPlan = nutritionistAgent.generateSevenDayPlan(
                profile,
                medical,
                performance,
                pantryResponse.getRecipes()
        );
        pantryResponse.setNutritionPlan(nutritionPlan);
        return pantryResponse;
    }
}
