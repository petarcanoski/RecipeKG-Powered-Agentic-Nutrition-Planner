package com.recipekg.planner.service;

import com.recipekg.planner.dto.UriWeekPlan;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class VarietyBrainService {

    public boolean isVaried(UriWeekPlan plan) {

        Map<String, Integer> counter = new HashMap<>();

        for (var d : plan.getWeekPlan()) {

            counter.merge(d.getBreakfast(), 1, Integer::sum);
            counter.merge(d.getLunch(), 1, Integer::sum);
            counter.merge(d.getDinner(), 1, Integer::sum);
        }

        for (int c : counter.values()) {

            if (c > 4) {
                return false;
            }
        }

        return true;
    }
}