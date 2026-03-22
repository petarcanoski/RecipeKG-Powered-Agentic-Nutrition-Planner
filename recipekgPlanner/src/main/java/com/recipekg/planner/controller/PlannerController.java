package com.recipekg.planner.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.WeeklyPlan;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.repository.WeeklyPlanRepository;
import com.recipekg.planner.service.PlanAdaptationService;
import com.recipekg.planner.service.PlannerService;
import com.recipekg.planner.service.ProgramTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planner")
@RequiredArgsConstructor
@CrossOrigin
public class PlannerController {

    private final PlannerService plannerService;
    private final PlanAdaptationService planAdaptationService;
    private final ProgramTimelineService programTimelineService;
    private final WeeklyPlanRepository planRepository;
    private final UserRepository userRepository;

    @PostMapping("/generate/{userId}")
    public WeeklyPlan generate(@PathVariable Long userId) throws JsonProcessingException {
        return plannerService.generateInitialPlan(userId);
    }

    @PostMapping("/next-week/{userId}")
    public WeeklyPlan nextWeek(@PathVariable Long userId) throws JsonProcessingException {
        return plannerService.generateNextWeek(userId);
    }

    @PostMapping("/adapt-week")
    public WeeklyPlan adaptWeek(
            @RequestParam Long userId,
            @RequestParam String event
    ) {
        return planAdaptationService.adaptCurrentWeek(userId, event);
    }

    @GetMapping("/current-plan/{userId}")
    public WeeklyPlan getCurrentPlan(@PathVariable Long userId) throws JsonProcessingException {

        programTimelineService.syncUserProgram(userId);

        User user =
                userRepository.findById(userId)
                        .orElseThrow();

        return planRepository
                .findByUserIdAndWeekNumber(
                        userId,
                        user.getCurrentWeek()
                )
                .orElseThrow();
    }
}