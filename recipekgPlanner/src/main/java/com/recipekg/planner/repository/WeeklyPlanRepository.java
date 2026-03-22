package com.recipekg.planner.repository;

import com.recipekg.planner.model.WeeklyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, Long> {

    Optional<WeeklyPlan> findByUserIdAndWeekNumber(Long userId, Integer weekNumber);
}