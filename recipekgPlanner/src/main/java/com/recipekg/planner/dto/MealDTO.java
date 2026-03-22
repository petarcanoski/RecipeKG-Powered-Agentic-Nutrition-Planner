package com.recipekg.planner.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealDTO {

    private String name;
    private Double calories;
    String mealUri;
    List<IngredientDTO> ingredients;

    @Override
    public String toString() {
        return "MealDTO{" +
                "uri='" + mealUri + '\'' +
                ", ingredients=" + ingredients +
                '}';
    }
}