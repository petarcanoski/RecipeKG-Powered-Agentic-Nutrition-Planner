package com.recipekg.planner.dto;

import lombok.Data;

import java.util.List;

@Data
public class UriWeekPlan {

    private List<UriDayPlan> weekPlan;
}
