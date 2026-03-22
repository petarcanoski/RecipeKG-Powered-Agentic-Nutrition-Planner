package com.recipekg.planner.service;

import com.recipekg.planner.dto.UriWeekPlan;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoryBrainService {

    public Set<String> extractUsedMeals(UriWeekPlan plan) {

        Set<String> used = new HashSet<>();

        plan.getWeekPlan().forEach(d -> {
            used.add(d.getBreakfast());
            used.add(d.getLunch());
            used.add(d.getDinner());
        });

        return used;
    }

    public String buildAvoidText(Set<String> used) {

        if (used.isEmpty()) return "None";

        return used.stream()
                .collect(Collectors.joining("\n"));
    }
}