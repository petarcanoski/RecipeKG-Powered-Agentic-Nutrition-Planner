package com.recipekg.planner.repository;

import com.recipekg.planner.model.WeeklyFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeeklyFeedbackRepository
        extends JpaRepository<WeeklyFeedback, Long> {

    Optional<WeeklyFeedback>
    findByUserIdAndWeekNumber(Long userId, Integer weekNumber);
}