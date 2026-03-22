package com.recipekg.planner.service.agents;

import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.model.WeeklyFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProgressAgentService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    public String generateNextWeekPlan(
            UserProfile profile,
            String previousPlanJson,
            WeeklyFeedback feedback
    ) {

        String prompt = """
You are an adaptive performance planner.

User finished previous week.

You must create improved 7-day plan.

Consider:
- adherence level
- weight progress
- sickness
- previous structure
- maintain safety

Return STRICT JSON ONLY

FORMAT:

{
 "weekPlan":[
   {
     "day":"",
     "breakfast":"",
     "lunch":"",
     "dinner":"",
     "workout":"",
     "notes":""
   }
 ]
}

PROFILE:
Age: %d
Weight: %.1f
Goal: %s
Diseases: %s
Allergies: %s
Activity: %s

FEEDBACK:
Adherence: %d
WeightChange: %.2f
Sickness: %s
Notes: %s

PREVIOUS PLAN:
%s
""".formatted(
                profile.getAge(),
                profile.getWeight(),
                profile.getGoal(),
                profile.getDiseases(),
                profile.getAllergies(),
                profile.getActivityLevel(),
                feedback.getAdherenceScore(),
                feedback.getWeightChange(),
                feedback.getSickness(),
                feedback.getNotes(),
                previousPlanJson
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

            return node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

        } catch (Exception e) {
            throw new RuntimeException("Progress agent parse error", e);
        }
    }
}