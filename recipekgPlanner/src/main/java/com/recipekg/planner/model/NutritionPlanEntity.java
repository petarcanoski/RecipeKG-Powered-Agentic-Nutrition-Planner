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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "nutrition_plan",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_nutrition_plan_user_week",
                columnNames = {"user_id", "week_number"}
        )
)
public class NutritionPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "goal_status")
    private String goalStatus;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "weekly_calories")
    private Double weeklyCalories;

    @Column(name = "weekly_protein")
    private Double weeklyProtein;

    @Column(name = "weekly_carbs")
    private Double weeklyCarbs;

    @Column(name = "weekly_fat")
    private Double weeklyFat;

    @Column(name = "weekly_sugar")
    private Double weeklySugar;

    @Column(name = "weekly_sodium")
    private Double weeklySodium;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "nutritionPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NutritionPlanDayEntity> days = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
