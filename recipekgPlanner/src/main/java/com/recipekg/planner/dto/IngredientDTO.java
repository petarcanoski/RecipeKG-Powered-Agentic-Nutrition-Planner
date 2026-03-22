package com.recipekg.planner.dto;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientDTO {
    String name;
    String quantity;
    String unit;
}
