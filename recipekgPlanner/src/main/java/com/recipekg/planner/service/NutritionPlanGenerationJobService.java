package com.recipekg.planner.service;

import com.recipekg.planner.response.FrontendNutritionPlanResponse;
import com.recipekg.planner.response.NutritionPlanGenerationJobResponse;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class NutritionPlanGenerationJobService {

    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    private final PlannerService plannerService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, NutritionPlanGenerationJobResponse> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> latestJobByUser = new ConcurrentHashMap<>();

    public NutritionPlanGenerationJobResponse start(Long userId) {
        Optional<NutritionPlanGenerationJobResponse> existingRunningJob = latestForUser(userId)
                .filter(job -> PENDING.equals(job.status()) || RUNNING.equals(job.status()));

        if (existingRunningJob.isPresent()) {
            return existingRunningJob.get();
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        NutritionPlanGenerationJobResponse pending = new NutritionPlanGenerationJobResponse(
                jobId,
                userId,
                PENDING,
                "Nutrition plan generation has been queued.",
                now,
                now,
                null
        );

        jobs.put(jobId, pending);
        latestJobByUser.put(userId, jobId);

        executorService.submit(() -> runJob(jobId, userId, now));

        return pending;
    }

    public Optional<NutritionPlanGenerationJobResponse> get(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Optional<NutritionPlanGenerationJobResponse> latestForUser(Long userId) {
        String jobId = latestJobByUser.get(userId);
        return jobId == null ? Optional.empty() : get(jobId);
    }

    private void runJob(String jobId, Long userId, LocalDateTime createdAt) {
        jobs.put(jobId, new NutritionPlanGenerationJobResponse(
                jobId,
                userId,
                RUNNING,
                "Nutrition plan generation is running.",
                createdAt,
                LocalDateTime.now(),
                null
        ));

        try {
            FrontendNutritionPlanResponse plan = plannerService.generateNutritionPlan(userId);
            jobs.put(jobId, new NutritionPlanGenerationJobResponse(
                    jobId,
                    userId,
                    COMPLETED,
                    "Nutrition plan generation completed.",
                    createdAt,
                    LocalDateTime.now(),
                    plan
            ));
        } catch (Exception e) {
            jobs.put(jobId, new NutritionPlanGenerationJobResponse(
                    jobId,
                    userId,
                    FAILED,
                    rootCauseMessage(e),
                    createdAt,
                    LocalDateTime.now(),
                    null
            ));
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
