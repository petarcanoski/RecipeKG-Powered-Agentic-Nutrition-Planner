package com.recipekg.planner.service;

import com.recipekg.planner.dto.MealDTO;
import com.recipekg.planner.dto.UriDayPlan;
import com.recipekg.planner.dto.UriWeekPlan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MealValidatorService {

    public boolean validate(UriWeekPlan plan, List<MealDTO> kgMeals) {

        Set<String> allowed =
                kgMeals.stream()
                        .map(MealDTO::getMealUri)
                        .collect(Collectors.toSet());

        for (UriDayPlan day : plan.getWeekPlan()) {

            if (!allowed.contains(day.getBreakfast())) return false;
            if (!allowed.contains(day.getLunch())) return false;
            if (!allowed.contains(day.getDinner())) return false;
        }

        return true;
    }

    private String extractName(String uri) {

        String raw = uri.substring(uri.lastIndexOf("/") + 1);

        raw = raw.replace("-", " ");
        raw = raw.replace("%20", " ");

        return raw;
    }
}