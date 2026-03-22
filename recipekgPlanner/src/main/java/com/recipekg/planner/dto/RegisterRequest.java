package com.recipekg.planner.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String email;
    private String password;

    private Integer age;
    private String gender;
    private Double height;
    private Double weight;
    private String bloodType;
    private String activityLevel;
    private String goal;
    private String allergies;
    private String diseases;
}