package com.recipekg.planner.dto;

import lombok.Data;

import java.util.List;

@Data
public class RegisterRequest {

    private String email;
    private String password;
    private String name;
    private String surname;

    private Integer age;
    private String gender;
    private Double height;
    private Double weight;
    private String bloodType;
    private String activityLevel;
    private String goal;
    private List<String> allergies;
    private List<String> diseases;
}
