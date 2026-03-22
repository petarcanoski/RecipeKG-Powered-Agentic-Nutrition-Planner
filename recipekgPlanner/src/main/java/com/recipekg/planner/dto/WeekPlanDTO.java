package com.recipekg.planner.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeekPlanDTO {

    private List<DayPlanDTO> weekPlan;
}