package com.recipekg.planner.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "weekly_plans")
public class WeeklyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer weekNumber;

    private LocalDate startDate;

    @Column(columnDefinition = "TEXT")
    private String planJson;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}