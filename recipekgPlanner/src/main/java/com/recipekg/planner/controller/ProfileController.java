package com.recipekg.planner.controller;

import com.recipekg.planner.dto.UserProfileDTO;
import com.recipekg.planner.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}")
    public UserProfileDTO getProfile(@PathVariable Long userId) {
        return profileService.getUserProfile(userId);
    }
}
