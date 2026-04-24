package com.recipekg.planner.controller;

import com.recipekg.planner.dto.LoginRequest;
import com.recipekg.planner.dto.RegisterRequest;
import com.recipekg.planner.model.User;
import com.recipekg.planner.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public void register(@RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public User login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout/{userId}")
    public void logout(@PathVariable Long userId) {
        // For now, this is a placeholder as logout is mainly handled on the client side
        // In a real implementation with JWT or sessions, this would invalidate the token/session
    }

    @PostMapping("/start/{userId}")
    public void startProgram(@PathVariable Long userId) {
        authService.startProgram(userId);
    }
}