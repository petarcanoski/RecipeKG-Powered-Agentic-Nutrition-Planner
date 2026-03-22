package com.recipekg.planner.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "weekly_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer weekNumber;

    private Integer adherenceScore;
    // how much user followed plan (0-100)

    private Double weightChange;
    // example +0.4 kg or -0.5 kg

    private String sickness;
    // example: flu / cold / injury / none

    @Column(length = 2000)
    private String notes;
    // free user comments

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}