package com.recipekg.planner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "nutrition_plan_meal")
public class NutritionPlanMealEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "nutrition_plan_day_id", nullable = false)
    private NutritionPlanDayEntity day;

    @Column(nullable = false)
    private String slot;

    @Column(name = "recipe_name", nullable = false, length = 500)
    private String recipeName;

    @Column(nullable = false)
    private Double servings;

    @Column(name = "ingredients_json", columnDefinition = "TEXT")
    private String ingredientsJson;

    private Double calories;

    private Double protein;

    private Double carbs;

    private Double fat;

    private Double sugar;

    private Double sodium;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
