package com.recipekg.planner.repository;

import com.recipekg.planner.model.NutritionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlanEntity, Long> {

    Optional<NutritionPlanEntity> findTopByUserIdAndWeekNumberOrderByUpdatedAtDesc(Long userId, Integer weekNumber);

    Optional<NutritionPlanEntity> findTopByUserIdAndWeekNumberAndGeneratedByOrderByUpdatedAtDesc(Long userId, Integer weekNumber, String generatedBy);

    Optional<NutritionPlanEntity> findTopByUserIdOrderByWeekNumberDescUpdatedAtDesc(Long userId);

    Optional<NutritionPlanEntity> findTopByUserIdAndGeneratedByOrderByWeekNumberDescUpdatedAtDesc(Long userId, String generatedBy);
}
