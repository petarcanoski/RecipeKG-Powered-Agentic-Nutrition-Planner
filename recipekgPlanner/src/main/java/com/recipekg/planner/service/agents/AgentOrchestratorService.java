package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentOrchestratorService {

    private final MedicalAgentService medicalAgent;
    private final NutritionAgentService nutritionAgent;
    private final FitnessAgentService fitnessAgent;
    private final PlanComposerService composer;

    public String generateFullPlan(UserProfile profile) {

        String medical =
                medicalAgent.generateMedicalAdvice(profile);

        String nutrition =
                nutritionAgent.generateNutritionPlan(profile, medical);

        String fitness =
                fitnessAgent.generateWorkoutPlan(profile, medical, nutrition);

        return composer.composeWeeklyPlan(medical, nutrition, fitness);
    }
}