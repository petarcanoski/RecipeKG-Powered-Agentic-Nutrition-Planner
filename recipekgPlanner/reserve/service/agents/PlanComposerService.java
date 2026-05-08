package com.recipekg.planner.service.agents;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanComposerService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    public String composeWeeklyPlan(
            String medicalJson,
            String nutritionJson,
            String fitnessJson
    ) {

        String prompt = """
You are a senior health performance planner.

TASK:
Create FINAL structured 7-day plan.

Requirements:
- combine nutrition + fitness + safety
- generate meals per day
- generate workout per day
- add daily short notes
- realistic schedule
- include rest day
- respect allergies and diseases
- STRICT JSON ONLY

Return format:

{
 "weekPlan":[
   {
     "day":"MONDAY",
     "breakfast":"",
     "lunch":"",
     "dinner":"",
     "workout":"",
     "notes":""
   }
 ]
}

MEDICAL:
%s

NUTRITION:
%s

FITNESS:
%s
""".formatted(medicalJson, nutritionJson, fitnessJson);

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
            throw new RuntimeException("Composer agent parse failed", e);
        }
    }
}