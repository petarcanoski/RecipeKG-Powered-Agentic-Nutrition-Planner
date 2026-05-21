package com.recipekg.planner.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserProfileDTO {
     private String name;
     private String surname;
     private String email;
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
