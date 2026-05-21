package com.recipekg.planner.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_profile_allergies", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "allergy", length = 255)
    private List<String> allergies;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_profile_diseases", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "disease", length = 255)
    private List<String> diseases;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;
}
