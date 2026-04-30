package com.recipekg.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.dto.WeekPlanDTO;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.model.WeeklyFeedback;
import com.recipekg.planner.model.WeeklyPlan;
import com.recipekg.planner.repository.UserProfileRepository;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.repository.WeeklyFeedbackRepository;
import com.recipekg.planner.repository.WeeklyPlanRepository;
import com.recipekg.planner.service.agents.AgentOrchestratorService;
import com.recipekg.planner.service.agents.ProgressAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PlannerService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final WeeklyPlanRepository planRepository;
    private final AgentOrchestratorService orchestrator;
    private final WeeklyFeedbackRepository weeklyFeedbackRepository;
    private final ProgressAgentService progressAgentService;
    private final PlanParserService planParserService;

    public WeeklyPlan generateInitialPlan(Long userId) throws JsonProcessingException {

        User user = userRepository.findById(userId).orElseThrow();

        UserProfile profile = profileRepository.findAll()
                .stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow();

        String planJson = orchestrator.generateFullPlan(profile).toString();

        WeekPlanDTO dto =
                planParserService.parseAndValidate(planJson);

        String safeJson =
                new ObjectMapper().writeValueAsString(dto);

        WeeklyPlan plan = WeeklyPlan.builder()
                .user(user)
                .weekNumber(1)
                .startDate(LocalDate.now())
                .planJson(safeJson)
                .build();

        return planRepository.save(plan);
    }


    @Transactional
    public WeeklyPlan generateNextWeek(Long userId) throws JsonProcessingException {

        User user =
                userRepository.findById(userId)
                        .orElseThrow();

        int nextWeek = user.getCurrentWeek() + 1;

        WeeklyPlan previousPlan =
                planRepository
                        .findByUserIdAndWeekNumber(
                                userId,
                                user.getCurrentWeek()
                        )
                        .orElseThrow();

        WeeklyFeedback feedback =
                weeklyFeedbackRepository
                        .findByUserIdAndWeekNumber(
                                userId,
                                user.getCurrentWeek()
                        )
                        .orElseThrow();

        UserProfile profile =
                profileRepository
                        .findByUserId(userId)
                        .orElseThrow();

        String newPlanJson =
                progressAgentService.generateNextWeekPlan(
                        profile,
                        previousPlan.getPlanJson(),
                        feedback
                );

        WeekPlanDTO dto = planParserService.parseAndValidate(newPlanJson);
        String safeJson = new ObjectMapper().writeValueAsString(dto);

        WeeklyPlan newPlan =
                WeeklyPlan.builder()
                        .weekNumber(nextWeek)
                        .startDate(
                                previousPlan
                                        .getStartDate()
                                        .plusDays(7)
                        )
                        .planJson(safeJson)
                        .user(user)
                        .build();


        planRepository.save(newPlan);

        user.setCurrentWeek(nextWeek);
        userRepository.save(user);

        return newPlan;
    }
}