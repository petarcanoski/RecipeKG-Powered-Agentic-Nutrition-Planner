package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FitnessAgentService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    public String generateWorkoutPlan(UserProfile profile,
                                      String medicalJson,
                                      String nutritionJson) {

        String prompt = """
You are an elite fitness trainer.

Create a SAFE 7-day training structure.

Rules:
- Respect medical advice
- Respect energy availability from nutrition
- Match training intensity to goal
- Include rest day
- Return STRICT JSON only
- No markdown

PROFILE:
Age: %d
Weight: %.1f
Goal: %s
ActivityLevel: %s
Diseases: %s

MEDICAL:
%s

NUTRITION:
%s
""".formatted(
                profile.getAge(),
                profile.getWeight(),
                profile.getGoal(),
                profile.getActivityLevel(),
                profile.getDiseases(),
                medicalJson,
                nutritionJson
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

            return text.trim();

        } catch (Exception e) {
            throw new RuntimeException("Fitness agent parse failed", e);
        }
    }
}