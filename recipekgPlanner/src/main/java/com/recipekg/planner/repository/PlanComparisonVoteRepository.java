package com.recipekg.planner.repository;

import com.recipekg.planner.model.PlanComparisonVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanComparisonVoteRepository extends JpaRepository<PlanComparisonVote, Long> {

    Optional<PlanComparisonVote> findByUserIdAndRecipeKgPlanIdAndGeminiPlanId(
            Long userId,
            Long recipeKgPlanId,
            Long geminiPlanId
    );

    long countByWinner(String winner);
}
