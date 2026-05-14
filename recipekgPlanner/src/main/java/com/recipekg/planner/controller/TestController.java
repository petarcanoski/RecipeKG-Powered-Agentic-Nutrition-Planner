package com.recipekg.planner.controller;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.UserProfileRepository;
import com.recipekg.planner.response.PantryResponse;
import com.recipekg.planner.service.FoodScientistService;
import com.recipekg.planner.service.agents.AgentOrchestratorService;
import com.recipekg.planner.service.agents.MedicalAgentService;
import com.recipekg.planner.service.agents.ProgressAgentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    private final MedicalAgentService medicalAgentService;
    private final UserProfileRepository userProfileRepository;
    private final FoodScientistService foodScientistService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final ProgressAgentService progressAgentService;


    public TestController(MedicalAgentService medicalAgentService, UserProfileRepository userProfileRepository, FoodScientistService foodScientistService, AgentOrchestratorService agentOrchestratorService, ProgressAgentService progressAgentService) {
        this.medicalAgentService = medicalAgentService;
        this.userProfileRepository = userProfileRepository;
        this.foodScientistService = foodScientistService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.progressAgentService = progressAgentService;
    }

    @Value("${gemini.api-key}")
    private String apiKey;

    @GetMapping("/test-gemini")
    @ResponseBody
    public String testGemini() {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey;

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> part = new HashMap<>();
        part.put("text", "Explain recursion in one sentence");

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            List candidates = (List) response.getBody().get("candidates");
            Map firstCandidate = (Map) candidates.get(0);
            Map contentMap = (Map) firstCandidate.get("content");
            List parts = (List) contentMap.get("parts");
            Map textPart = (Map) parts.get(0);

            return (String) textPart.get("text");

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test-medical/{id}")
    public PantryResponse testMedicalAgent(@PathVariable Long id){

        UserProfile userProfile=userProfileRepository.findByUserId(id).get();
//        MedicalManifest m= medicalAgentService.generateMedicalAdvice(userProfile);
//        return foodScientistService.buildSafeCandidateQuery(m);
        return agentOrchestratorService.generateFullPlan(userProfile);
    }


}