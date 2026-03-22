package com.recipekg.planner.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayPlanDTO {

    private String day;
    private String breakfast;
    private String lunch;
    private String dinner;
    private String workout;
    private String notes;
}