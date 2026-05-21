package com.recipekg.planner.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "nutrition_plan_day",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_nutrition_plan_day",
                columnNames = {"nutrition_plan_id", "day_number"}
        )
)
public class NutritionPlanDayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "nutrition_plan_id", nullable = false)
    private NutritionPlanEntity nutritionPlan;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "total_calories")
    private Double totalCalories;

    @Column(name = "total_protein")
    private Double totalProtein;

    @Column(name = "total_carbs")
    private Double totalCarbs;

    @Column(name = "total_fat")
    private Double totalFat;

    @Column(name = "total_sugar")
    private Double totalSugar;

    @Column(name = "total_sodium")
    private Double totalSodium;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<NutritionPlanMealEntity> meals = new ArrayList<>();
}
