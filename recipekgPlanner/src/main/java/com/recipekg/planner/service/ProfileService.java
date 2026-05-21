package com.recipekg.planner.service;

import com.recipekg.planner.dto.UserProfileDTO;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.UserProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileService {

    private final UserProfileRepository userProfileRepository;

    public ProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfileDTO getUserProfile(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found"));
        User user = profile.getUser();

        return UserProfileDTO.builder()
                .name(user.getName())
                .surname(user.getSurname())
                .email(user.getEmail())
                .age(profile.getAge())
                .gender(profile.getGender())
                .height(profile.getHeight())
                .weight(profile.getWeight())
                .bloodType(profile.getBloodType())
                .activityLevel(profile.getActivityLevel())
                .goal(profile.getGoal())
                .allergies(profile.getAllergies() == null ? List.of() : profile.getAllergies())
                .diseases(profile.getDiseases() == null ? List.of() : profile.getDiseases())
                .build();
    }
}
