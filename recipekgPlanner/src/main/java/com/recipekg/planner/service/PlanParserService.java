package com.recipekg.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.recipekg.planner.dto.WeekPlanDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanParserService {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public WeekPlanDTO parseAndValidate(String aiJson) {

        try {

            WeekPlanDTO dto =
                    mapper.readValue(aiJson, WeekPlanDTO.class);

            if (dto.getWeekPlan() == null ||
                    dto.getWeekPlan().size() != 7) {

                throw new RuntimeException(
                        "AI returned invalid week size"
                );
            }

            dto.getWeekPlan().forEach(day -> {

                if (day.getDay() == null ||
                        day.getBreakfast() == null ||
                        day.getWorkout() == null) {

                    throw new RuntimeException(
                            "Invalid day structure"
                    );
                }
            });

            return dto;

        } catch (Exception e) {
            throw new RuntimeException(
                    "AI PLAN PARSE FAILED", e
            );
        }
    }
}