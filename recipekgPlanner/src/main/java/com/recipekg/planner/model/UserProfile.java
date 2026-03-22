package com.recipekg.planner.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer age;
    private String gender;
    private Double height;
    private Double weight;
    private String bloodType;
    private String activityLevel;
    private String goal;

    @Column(length = 2000)
    private String allergies;

    @Column(length = 2000)
    private String diseases;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;
}