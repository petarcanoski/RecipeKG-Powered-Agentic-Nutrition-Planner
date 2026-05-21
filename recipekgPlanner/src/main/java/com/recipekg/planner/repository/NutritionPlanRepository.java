package com.recipekg.planner.repository;

import com.recipekg.planner.model.NutritionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlanEntity, Long> {

    Optional<NutritionPlanEntity> findByUserIdAndWeekNumber(Long userId, Integer weekNumber);

    Optional<NutritionPlanEntity> findTopByUserIdOrderByWeekNumberDesc(Long userId);
}
