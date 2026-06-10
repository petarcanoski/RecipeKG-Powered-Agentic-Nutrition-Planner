package com.recipekg.planner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "nutrition_plan_llm_trace")
public class NutritionPlanLlmTraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nutrition_plan_id", nullable = false)
    private NutritionPlanEntity nutritionPlan;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "iteration_number", nullable = false)
    private Integer iterationNumber;

    @Column(nullable = false, length = 50)
    private String phase;

    @Column(length = 50)
    private String model;

    @Column(length = 50)
    private String status;

    @Column(name = "recipe_ids_sent_json", columnDefinition = "TEXT")
    private String recipeIdsSentJson;

    @Column(name = "selected_recipe_ids_json", columnDefinition = "TEXT")
    private String selectedRecipeIdsJson;

    @Column(name = "validation_result_json", columnDefinition = "TEXT")
    private String validationResultJson;

    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
