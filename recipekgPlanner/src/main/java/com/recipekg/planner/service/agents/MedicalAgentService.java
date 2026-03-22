package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MedicalAgentService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    public String generateMedicalAdvice(UserProfile profile) {

            String prompt = """
You are a medical professional.

Create SAFE weekly medical recommendations.

Return STRICT JSON only.

PROFILE:
Age: %d
Weight: %.1f
Diseases: %s
Allergies: %s
Goal: %s
ActivityLevel: %s
""".formatted(
                profile.getAge(),
                profile.getWeight(),
                profile.getDiseases(),
                profile.getAllergies(),
                profile.getGoal(),
                profile.getActivityLevel()
        );


        Map<String, Object> body =
                Map.of(
                        "contents", new Object[]{
                                Map.of(
                                        "parts", new Object[]{
                                                Map.of("text", prompt)
                                        }
                                )
                        }
                );

        String raw = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            var node = mapper.readTree(raw);

            String text =
                    node.get("candidates")
                            .get(0)
                            .get("content")
                            .get("parts")
                            .get(0)
                            .get("text")
                            .asText();

            // remove ```json ``` if model still adds it
            text = text.replace("```json", "")
                    .replace("```", "")
                    .trim();

            return text;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }
}