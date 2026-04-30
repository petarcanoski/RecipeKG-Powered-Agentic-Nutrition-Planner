package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class MedicalAgentService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    public MedicalAgentService(WebClient webClient) {
        this.webClient = webClient;
    }

    // ⭐ Changed return type to MedicalManifest
    public MedicalManifest generateMedicalAdvice(UserProfile profile) {

        String prompt = """
        You are an expert Clinical Dietitian and Medical AI Agent.
        Your task is to analyze the user's biological profile and generate a STRICT JSON manifest of dietary constraints.
        
        CRITICAL RULES FOR TERMINOLOGY & STATUS:
        1. STATUS DETERMINATION: 
           - If 'Allergies' is "None" AND 'Diseases/Conditions' is "None", you MUST set "status": "UNCONSTRAINED". 
           - Do NOT apply general population preventative guidelines (like WHO sodium caps) to healthy users.
           - ONLY set "status": "CONSTRAINED" if the user has a specific allergy, disease, or clinical condition requiring strict filtering.
        2. 'hard_exclusions': You must map the user's allergies to the FoodOn ontology format.
                Study the following examples to understand the database's specific labeling pattern:
                EXAMPLES:
                - User: "I'm allergic to dairy and milk." -> Output: ["dairy food product"]
                - User: "I have a severe nut." -> Output: ["nut food product"]
                - User: "No gluten or wheat." -> Output: ["wheat food product"]
                - User: "Allergic to eggs." -> Output: ["egg food product"]
        3. 'nutrient_caps': You MUST use standard USDA nutrient names (e.g., "Sodium", "Carbohydrate, by difference").
        4. IF UNCONSTRAINED: Leave the 'hard_exclusions', 'nutrient_caps', and 'required_boosts' arrays completely EMPTY.
        
        USER PROFILE:
        Age: %d
        Gender: %s
        Height (cm): %.1f
        Weight (kg): %.1f
        Blood Type: %s
        Activity Level: %s
        Goal: %s
        Allergies: %s
        Diseases/Conditions: %s
        
        EXPECTED JSON SCHEMA:
        {
          "status": "CONSTRAINED",
          "constraints": {
            "hard_exclusions": ["<String>"],
            "nutrient_caps": [
              { "nutrient": "<String>", "max_value": <Number>, "unit": "<String>" }
            ],
            "required_boosts": ["<String>"]
          },
          "medical_rationale": "<String detailing the clinical reasoning>"
        }
        """.formatted(
                profile.getAge(),
                profile.getGender(),
                profile.getHeight(),
                profile.getWeight(),
                profile.getBloodType(),
                profile.getActivityLevel(),
                profile.getGoal(),
                profile.getAllergies(),
                profile.getDiseases()
        );

        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                },
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        String raw = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node = mapper.readTree(raw);
            String jsonText = node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

            return mapper.readValue(jsonText, MedicalManifest.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response into MedicalManifest", e);
        }
    }
}