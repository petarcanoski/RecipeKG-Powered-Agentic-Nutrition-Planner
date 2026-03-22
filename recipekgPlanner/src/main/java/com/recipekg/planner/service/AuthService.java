package com.recipekg.planner.service;

import com.recipekg.planner.dto.LoginRequest;
import com.recipekg.planner.dto.RegisterRequest;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.UserProfileRepository;
import com.recipekg.planner.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;

    public void register(RegisterRequest request) {

        User user = User.builder()
                .email(request.getEmail())
                .password(request.getPassword()) // later we hash
                .programStarted(false)
                .currentWeek(0)
                .build();

        userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .age(request.getAge())
                .gender(request.getGender())
                .height(request.getHeight())
                .weight(request.getWeight())
                .bloodType(request.getBloodType())
                .activityLevel(request.getActivityLevel())
                .goal(request.getGoal())
                .allergies(request.getAllergies())
                .diseases(request.getDiseases())
                .user(user)
                .build();

        profileRepository.save(profile);
    }

    public User login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return user;
    }

    public void startProgram(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow();

        user.setProgramStarted(true);
        user.setProgramStartDate(LocalDate.now());
        user.setCurrentWeek(1);

        userRepository.save(user);
    }
}