package com.recipekg.planner.service;

import com.recipekg.planner.dto.PlanComparisonVoteRequest;
import com.recipekg.planner.model.NutritionPlanEntity;
import com.recipekg.planner.model.PlanComparisonVote;
import com.recipekg.planner.model.User;
import com.recipekg.planner.repository.PlanComparisonVoteRepository;
import com.recipekg.planner.repository.UserRepository;
import com.recipekg.planner.response.PlanComparisonScoreResponse;
import com.recipekg.planner.response.PlanComparisonVoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanComparisonVoteService {

    public static final String RECIPE_KG = "RECIPE_KG";
    public static final String GEMINI = "GEMINI";
    public static final String TIE = "TIE";

    private final UserRepository userRepository;
    private final NutritionPlanPersistenceService nutritionPlanPersistenceService;
    private final PlanComparisonVoteRepository voteRepository;

    @Transactional
    public PlanComparisonVoteResponse vote(PlanComparisonVoteRequest request) {
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow();

        NutritionPlanEntity recipeKgPlan = nutritionPlanPersistenceService
                .findLatestEntityByUserAndGeneratedBy(user.getId(), NutritionPlanPersistenceService.RECIPE_KG_AGENT)
                .orElseThrow(() -> new IllegalStateException("RecipeKG plan is not available for voting"));

        NutritionPlanEntity geminiPlan = nutritionPlanPersistenceService
                .findLatestEntityByUserAndGeneratedBy(user.getId(), NutritionPlanPersistenceService.DIRECT_GEMINI)
                .orElseThrow(() -> new IllegalStateException("Gemini plan is not available for voting"));

        String winner = normalizeWinner(request.getWinner());

        PlanComparisonVote vote = voteRepository
                .findByUserIdAndRecipeKgPlanIdAndGeminiPlanId(
                        user.getId(),
                        recipeKgPlan.getId(),
                        geminiPlan.getId()
                )
                .orElseGet(PlanComparisonVote::new);

        vote.setUser(user);
        vote.setRecipeKgPlan(recipeKgPlan);
        vote.setGeminiPlan(geminiPlan);
        vote.setWinner(winner);
        vote.setReason(cleanReason(request.getReason()));

        PlanComparisonVote saved = voteRepository.save(vote);
        return toResponse(saved, "Vote saved.");
    }

    @Transactional(readOnly = true)
    public PlanComparisonScoreResponse score() {
        long recipeKgWins = voteRepository.countByWinner(RECIPE_KG);
        long geminiWins = voteRepository.countByWinner(GEMINI);
        long ties = voteRepository.countByWinner(TIE);

        return new PlanComparisonScoreResponse(
                recipeKgWins,
                geminiWins,
                ties,
                recipeKgWins + geminiWins + ties
        );
    }

    private PlanComparisonVoteResponse toResponse(PlanComparisonVote vote, String message) {
        return new PlanComparisonVoteResponse(
                vote.getId(),
                vote.getUser().getId(),
                vote.getRecipeKgPlan().getId(),
                vote.getGeminiPlan().getId(),
                vote.getWinner(),
                vote.getReason(),
                message
        );
    }

    private String normalizeWinner(String winner) {
        if (winner == null) {
            throw new IllegalArgumentException("winner is required");
        }

        String normalized = winner.trim().toUpperCase();
        if (!RECIPE_KG.equals(normalized) && !GEMINI.equals(normalized) && !TIE.equals(normalized)) {
            throw new IllegalArgumentException("winner must be RECIPE_KG, GEMINI, or TIE");
        }

        return normalized;
    }

    private String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }

        return reason.trim();
    }
}
