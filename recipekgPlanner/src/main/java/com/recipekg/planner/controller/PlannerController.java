package com.recipekg.planner.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.WeeklyPlan;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.repository.WeeklyPlanRepository;
import com.recipekg.planner.response.FrontendNutritionPlanResponse;
import com.recipekg.planner.response.NutritionPlanGenerationJobResponse;
import com.recipekg.planner.response.NutritionPlanStatusResponse;
import com.recipekg.planner.service.DirectGeminiNutritionPlanService;
import com.recipekg.planner.service.NutritionPlanGenerationJobService;
import com.recipekg.planner.service.PlanAdaptationService;
import com.recipekg.planner.service.PlannerService;
import com.recipekg.planner.service.ProgramTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final NutritionPlanGenerationJobService nutritionPlanGenerationJobService;
    private final DirectGeminiNutritionPlanService directGeminiNutritionPlanService;

    @PostMapping("/generate/{userId}")
    public NutritionPlanGenerationJobResponse generate(@PathVariable Long userId) {
        return nutritionPlanGenerationJobService.start(userId);
    }

    @PostMapping("/generate-direct-gemini/{userId}")
    public FrontendNutritionPlanResponse generateDirectGemini(@PathVariable Long userId) {
        return directGeminiNutritionPlanService.generateAndSave(userId);
    }

    @GetMapping("/generate/status/{jobId}")
    public NutritionPlanGenerationJobResponse generationStatus(@PathVariable String jobId) {
        return nutritionPlanGenerationJobService.get(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generation job not found"));
    }

    @GetMapping("/nutrition-plan/current/{userId}")
    public NutritionPlanStatusResponse currentNutritionPlan(
            @PathVariable Long userId,
            @RequestParam(required = false) String generatedBy
    ) {
        return plannerService.getCurrentNutritionPlan(userId, generatedBy)
                .map(plan -> new NutritionPlanStatusResponse(
                        "COMPLETED",
                        "Nutrition plan is available.",
                        latestJobId(userId),
                        plan
                ))
                .orElseGet(() -> nutritionPlanGenerationJobService.latestForUser(userId)
                        .map(job -> new NutritionPlanStatusResponse(
                                job.status(),
                                job.message(),
                                job.jobId(),
                                job.nutritionPlan()
                        ))
                        .orElse(new NutritionPlanStatusResponse(
                                "NOT_FOUND",
                                "No nutrition plan has been generated for this user yet.",
                                null,
                                null
                        )));
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

    private String latestJobId(Long userId) {
        return nutritionPlanGenerationJobService.latestForUser(userId)
                .map(NutritionPlanGenerationJobResponse::jobId)
                .orElse(null);
    }
}
