package com.recipekg.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.WeeklyPlan;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.repository.WeeklyPlanRepository;
import com.recipekg.planner.service.ai.NvidiaChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlanAdaptationService {

    private final WeeklyPlanRepository planRepository;
    private final UserRepository userRepository;
    private final NvidiaChatClient nvidiaChatClient;

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

            JsonNode newRemaining =
                    mapper.readTree(extractJsonPayload(nvidiaChatClient.complete(prompt)));

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

    private String stripCodeFence(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").trim();
            trimmed = trimmed.replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private String extractJsonPayload(String text) {
        String trimmed = stripCodeFence(text);
        if (trimmed.isBlank()) return trimmed;

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
