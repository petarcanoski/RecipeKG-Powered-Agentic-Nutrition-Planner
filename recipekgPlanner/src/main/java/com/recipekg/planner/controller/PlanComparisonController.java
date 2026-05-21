package com.recipekg.planner.controller;

import com.recipekg.planner.dto.PlanComparisonVoteRequest;
import com.recipekg.planner.response.PlanComparisonScoreResponse;
import com.recipekg.planner.response.PlanComparisonVoteResponse;
import com.recipekg.planner.service.PlanComparisonVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comparisons")
@RequiredArgsConstructor
@CrossOrigin
public class PlanComparisonController {

    private final PlanComparisonVoteService voteService;

    @PostMapping("/vote")
    public PlanComparisonVoteResponse vote(@RequestBody PlanComparisonVoteRequest request) {
        return voteService.vote(request);
    }

    @GetMapping("/score")
    public PlanComparisonScoreResponse score() {
        return voteService.score();
    }
}
