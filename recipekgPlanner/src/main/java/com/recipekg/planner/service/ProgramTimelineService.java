package com.recipekg.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.recipekg.planner.model.User;
import com.recipekg.planner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ProgramTimelineService {

    private final UserRepository userRepository;
    private final PlannerService plannerService;

    public void syncUserProgram(Long userId) throws JsonProcessingException {

        User user =
                userRepository.findById(userId)
                        .orElseThrow();

        if (user.getProgramStartDate().isAfter(LocalDate.now()))
            return;

        LocalDate start =
                user.getProgramStartDate();

        long days =
                ChronoUnit.DAYS.between(start, LocalDate.now());

        int calculatedWeek =
                (int) (days / 7) + 1;

        while (user.getCurrentWeek() < calculatedWeek) {

            plannerService.generateNextWeek(userId);

            user =
                    userRepository.findById(userId)
                            .orElseThrow();
        }
    }
}