package com.recipekg.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.WeeklyPlan;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.repository.WeeklyPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanAdaptationService {

    private final WeeklyPlanRepository planRepository;
    private final UserRepository userRepository;
    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    public WeeklyPlan adaptCurrentWeek(Long userId, String event) {

        User user = userRepository.findById(userId).orElseThrow();

        WeeklyPlan currentPlan =
                planRepository
                        .findByUserIdAndWeekNumber(
                                userId,
                                user.getCurrentWeek()
                        )
                        .orElseThrow();

        try {

            ObjectMapper mapper = new ObjectMapper();

            JsonNode root =
                    mapper.readTree(currentPlan.getPlanJson());

            JsonNode weekArray =
                    root.get("weekPlan");

            LocalDate today = LocalDate.now();

            int todayIndex =
                    today.getDayOfWeek().getValue() - 1;

            List<JsonNode> completed =
                    new ArrayList<>();

            List<JsonNode> remaining =
                    new ArrayList<>();

            for (int i = 0; i < weekArray.size(); i++) {

                if (i < todayIndex)
                    completed.add(weekArray.get(i));
                else
                    remaining.add(weekArray.get(i));
            }

            String prompt = """
User event occurred: %s

Regenerate remaining weekly schedule.

Rules:
- reduce intensity if sickness
- ensure recovery
- keep nutrition realistic
- STRICT JSON array format

Return only:

[
 { "day":"", "breakfast":"", "lunch":"", "dinner":"", "workout":"", "notes":"" }
]
""".formatted(event);

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

            String raw =
                    webClient.post()
                            .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            JsonNode ai =
                    mapper.readTree(raw)
                            .get("candidates")
                            .get(0)
                            .get("content")
                            .get("parts")
                            .get(0)
                            .get("text");

            JsonNode newRemaining =
                    mapper.readTree(ai.asText());

            List<JsonNode> merged =
                    new ArrayList<>(completed);

            newRemaining.forEach(merged::add);

            Map<String, Object> finalJson =
                    Map.of("weekPlan", merged);

            currentPlan.setPlanJson(
                    mapper.writeValueAsString(finalJson)
            );

            return planRepository.save(currentPlan);

        } catch (Exception e) {
            throw new RuntimeException("Adaptation failed", e);
        }
    }
}