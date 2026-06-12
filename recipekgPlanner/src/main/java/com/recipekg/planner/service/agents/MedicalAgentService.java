package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.service.ai.NvidiaChatClient;
import org.springframework.stereotype.Service;

@Service
public class MedicalAgentService {

    private final NvidiaChatClient nvidiaChatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public MedicalAgentService(NvidiaChatClient nvidiaChatClient) {
        this.nvidiaChatClient = nvidiaChatClient;
    }


    public MedicalManifest generateMedicalAdvice(UserProfile profile) {

        String prompt = """
        You are an expert Clinical Dietitian and Medical AI Agent.
        Your task is to analyze the user's biological profile and generate a STRICT JSON manifest of dietary constraints.
        
        CRITICAL RULES FOR TERMINOLOGY & STATUS:
        1. STATUS DETERMINATION: 
           - If 'Allergies' is "None" AND 'Diseases/Conditions' is "None", you MUST set "status": "UNCONSTRAINED". 
           - ONLY set "status": "CONSTRAINED" if the user has a specific allergy, disease, or clinical condition requiring strict filtering.
        2. 'hard_exclusions': Your job is to act as a Keyword Unroller. You must translate the user's dietary restrictions into a flat JSON array of singular, root ingredient keywords and common foods that contain them.
                - Do NOT use broad ontology categories. Unroll them into specific, high-risk ingredients.
                - Strip pluralization (e.g., use "peanut" not "peanuts").
                
                EXAMPLES:
                - User: "I have a fish allergy." -> Output: ["fish", "salmon", "tuna", "cod", "trout"]
                - User: "I have a dairy allergy" -> Output: ["milk", "cheese", "butter", "whey"]
                - Uer: "I have a gluten allergy" -> Output: ["wheat", "bread", "flour", "pasta", "crouton", "pretzel"]
                
        
        3. 'nutrient_caps' RULES:
                - Each entry must contain exactly one USDA nutrient name.
                - Do NOT combine nutrients (no commas, no "and", no grouping).
                - Do NOT repeat the same nutrient more than once.
                - Use canonical USDA names only (examples: "Carbs", "Sugar",
                "Sodium", "Energy", "Protein", "Fat").
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
            ]
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

        String raw = nvidiaChatClient.complete(prompt);

        try {
            return parseMedicalManifest(raw);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse NVIDIA response into MedicalManifest", e);
        }
    }

        private MedicalManifest parseMedicalManifest(String jsonText) throws Exception {
                String trimmed = extractJsonPayload(jsonText);

                if (trimmed.startsWith("```")) {
                        int firstLineEnd = trimmed.indexOf('\n');
                        if (firstLineEnd >= 0) {
                                trimmed = trimmed.substring(firstLineEnd + 1);
                        }
                        int lastFence = trimmed.lastIndexOf("```");
                        if (lastFence >= 0) {
                                trimmed = trimmed.substring(0, lastFence);
                        }
                        trimmed = trimmed.trim();
                }

                if (trimmed.startsWith("[")) {
                        JsonNode arrayNode = mapper.readTree(trimmed);
                        if (arrayNode.isArray() && arrayNode.size() > 0) {
                                return mapper.treeToValue(arrayNode.get(0), MedicalManifest.class);
                        }
                }

                return mapper.readValue(trimmed, MedicalManifest.class);
        }

        private String extractJsonPayload(String text) {
                String trimmed = text == null ? "" : text.trim();
                if (trimmed.startsWith("```")) {
                        trimmed = trimmed.replaceFirst("^```(?:json)?", "").trim();
                        trimmed = trimmed.replaceFirst("```$", "").trim();
                }

                int objectStart = trimmed.indexOf('{');
                int arrayStart = trimmed.indexOf('[');
                int start;

                if (objectStart < 0) {
                        start = arrayStart;
                } else if (arrayStart < 0) {
                        start = objectStart;
                } else {
                        start = Math.min(objectStart, arrayStart);
                }

                if (start < 0) return trimmed;

                char opener = trimmed.charAt(start);
                char closer = opener == '[' ? ']' : '}';
                int end = trimmed.lastIndexOf(closer);
                if (end <= start) return trimmed.substring(start).trim();

                return trimmed.substring(start, end + 1).trim();
        }
}
