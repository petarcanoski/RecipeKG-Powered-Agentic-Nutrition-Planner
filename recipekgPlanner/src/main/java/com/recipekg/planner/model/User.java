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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    

    @Column(unique = true, nullable = false)
    private String email;

    private String name;

    private String surname;

    @Column(nullable = false)
    private String password;

    private boolean programStarted;

    private LocalDate programStartDate;

    private Integer currentWeek;
}
