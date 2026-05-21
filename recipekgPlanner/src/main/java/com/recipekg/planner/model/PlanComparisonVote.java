package com.recipekg.planner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "plan_comparison_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_plan_comparison_vote_pair",
                columnNames = {"user_id", "recipekg_plan_id", "gemini_plan_id"}
        )
)
public class PlanComparisonVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "recipekg_plan_id", nullable = false)
    private NutritionPlanEntity recipeKgPlan;

    @ManyToOne
    @JoinColumn(name = "gemini_plan_id", nullable = false)
    private NutritionPlanEntity geminiPlan;

    @Column(nullable = false, length = 30)
    private String winner;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
