package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutrientTarget;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.service.MacroCalculatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerformanceAgentService {

    private final WebClient webClient;
    private final MacroCalculatorService macroCalculatorService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.retry-503-attempts:5}")
    private int retry503Attempts;

    @Value("${gemini.retry-base-delay-ms:10000}")
    private long retryBaseDelayMillis;

    @Value("${gemini.retry-max-delay-ms:90000}")
    private long retryMaxDelayMillis;

    public PerformanceManifest generatePerformanceManifest(UserProfile profile, MedicalManifest medicalManifest) {
        try {
            String prompt = buildPrompt(profile, medicalManifest);
            String text = callGemini(prompt);
            PerformanceManifest manifest = parsePerformanceManifest(text);
            if (isUsable(manifest)) {
                return manifest;
            }
        } catch (Exception e) {
            System.err.println("Performance agent failed; using deterministic fallback: " + e.getMessage());

        }
        return null;
//        MacroCalculatorService.MacroTargets targets = macroCalculatorService.calculate(profile);
//        return fallbackManifest(profile, targets);
    }

    private String buildPrompt(UserProfile profile, MedicalManifest medicalManifest) {
        String medicalJson = "";
        try {
            medicalJson = mapper.writeValueAsString(medicalManifest);
        } catch (Exception ignored) {
        }

        return """
You are a sports nutrition performance agent.

Calculate performance nutrition targets for this user and return STRICT JSON only.
These targets are performance-based, not medical hard constraints.
Use the user's goal, body metrics, and activity level to estimate daily calories, protein, carbs, and fat.
Then derive per-serving targets suitable for ranking individual recipes.
Respect the medical manifest when choosing reasonable upper/lower ranges, but do not duplicate medical hard exclusions.

Return exactly this shape:
{
  "goalStatus": "HYPERTROPHY|FAT_LOSS|MAINTENANCE|ENDURANCE",
  "dailyTargets": [
    {"nutrient":"calories","min":0,"target":0,"max":0,"unit":"kcal","period":"day"},
    {"nutrient":"protein","min":0,"target":0,"max":0,"unit":"g","period":"day"},
    {"nutrient":"carbs","min":0,"target":0,"max":0,"unit":"g","period":"day"},
    {"nutrient":"fat","min":0,"target":0,"max":0,"unit":"g","period":"day"}
  ],
  "perServingTargets": [
    {"nutrient":"calories","min":0,"target":0,"max":0,"unit":"kcal","period":"serving"},
    {"nutrient":"protein","min":0,"target":0,"max":0,"unit":"g","period":"serving"},
    {"nutrient":"carbs","min":0,"target":0,"max":0,"unit":"g","period":"serving"},
    {"nutrient":"fat","min":0,"target":0,"max":0,"unit":"g","period":"serving"}
  ],
  "rationale": "short explanation"
}

PROFILE:
Age: %d
Gender: %s
WeightKg: %.2f
HeightCm: %.2f
Goal: %s
ActivityLevel: %s

MEDICAL MANIFEST:
%s
""".formatted(
                safeInt(profile.getAge()),
                safe(profile.getGender()),
                safeDouble(profile.getWeight()),
                safeDouble(profile.getHeight()),
                safe(profile.getGoal()),
                safe(profile.getActivityLevel()),
                medicalJson
        );
    }

    private String callGemini(String prompt) {
        Map<String, Object> body =
                Map.of(
                        "contents", new Object[]{
                                Map.of(
                                        "parts", new Object[]{
                                                Map.of("text", prompt)
                                        }
                                )
                        }
                );

        String raw = callGeminiRaw(body);

        try {
            JsonNode node = mapper.readTree(raw);
            return node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText()
                    .trim();
        } catch (Exception e) {
            throw new RuntimeException("Performance agent response parse failed", e);
        }
    }

    private String callGeminiRaw(Map<String, Object> body) {
        int maxAttempts = Math.max(1, retry503Attempts + 1);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callGeminiOnce(body);
            } catch (WebClientResponseException e) {
                lastError = e;
                if (!isRetryableGeminiStatus(e) || attempt >= maxAttempts) break;

                retryAfterDelay(
                        "Performance agent Gemini call returned " + e.getStatusCode().value() + " " + e.getStatusText(),
                        attempt,
                        maxAttempts
                );
            } catch (WebClientRequestException e) {
                lastError = e;
                if (attempt >= maxAttempts) break;

                retryAfterDelay("Performance agent Gemini request failed before receiving a response: " + rootCauseMessage(e), attempt, maxAttempts);
            }
        }

        throw lastError == null ? new RuntimeException("Performance agent Gemini request failed") : lastError;
    }

    private String callGeminiOnce(Map<String, Object> body) {
        return webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private void retryAfterDelay(String reason, int attempt, int maxAttempts) {
        long delayMillis = retryDelayMillis(attempt);
        System.err.printf(
                "%s; retrying attempt %d/%d after %dms.%n",
                reason,
                attempt + 1,
                maxAttempts,
                delayMillis
        );
        sleepBeforeRetry(delayMillis);
    }

    private boolean isRetryableGeminiStatus(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private long retryDelayMillis(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 4);
        long delay = retryBaseDelayMillis * multiplier;
        return Math.min(delay, retryMaxDelayMillis);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry Performance agent Gemini request", e);
        }
    }

    private PerformanceManifest parsePerformanceManifest(String jsonText) throws Exception {
        String trimmed = stripCodeFence(jsonText);
        JsonNode node = mapper.readTree(trimmed);
        if (node.isArray() && !node.isEmpty()) {
            node = node.get(0);
        }
        return mapper.treeToValue(node, PerformanceManifest.class);
    }

    private String stripCodeFence(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").trim();
            trimmed = trimmed.replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private boolean isUsable(PerformanceManifest manifest) {
        return manifest != null
                && manifest.goalStatus() != null
                && !manifest.goalStatus().isBlank()
                && manifest.dailyTargets() != null
                && !manifest.dailyTargets().isEmpty()
                && manifest.perServingTargets() != null
                && !manifest.perServingTargets().isEmpty();
    }

    private PerformanceManifest fallbackManifest(UserProfile profile, MacroCalculatorService.MacroTargets targets) {
        String goalStatus = classifyGoal(profile.getGoal(), profile.getActivityLevel());

        double calories = targets.getCalories();
        double protein = targets.getProtein();
        double carbs = targets.getCarbs();
        double fat = targets.getFats();

        List<NutrientTarget> dailyTargets = List.of(
                daily("calories", calories, "kcal"),
                daily("protein", protein, "g"),
                daily("carbs", carbs, "g"),
                daily("fat", fat, "g")
        );

        List<NutrientTarget> perServingTargets = List.of(
                serving("calories", calories / 4.0, "kcal"),
                new NutrientTarget("protein", protein / 5.0, protein / 4.0, protein / 3.0, "g", "serving"),
                serving("carbs", carbs / 4.0, "g"),
                serving("fat", fat / 4.0, "g")
        );

        return new PerformanceManifest(
                goalStatus,
                dailyTargets,
                perServingTargets,
                "Deterministic fallback based on Mifflin-St Jeor TDEE, activity level, and stated goal."
        );
    }

    private NutrientTarget daily(String nutrient, double target, String unit) {
        return new NutrientTarget(nutrient, target * 0.90, target, target * 1.10, unit, "day");
    }

    private NutrientTarget serving(String nutrient, double target, String unit) {
        return new NutrientTarget(nutrient, target * 0.70, target, target * 1.30, unit, "serving");
    }

    private String classifyGoal(String goal, String activityLevel) {
        String normalizedGoal = goal == null ? "" : goal.trim().toUpperCase();
        String normalizedActivity = activityLevel == null ? "" : activityLevel.trim().toUpperCase();

        if (normalizedGoal.contains("GAIN") || normalizedGoal.contains("MUSCLE") || normalizedGoal.contains("HYPERTROPHY")) {
            return "HYPERTROPHY";
        }
        if (normalizedGoal.contains("LOSE") || normalizedGoal.contains("LOSS") || normalizedGoal.contains("CUT")) {
            return "FAT_LOSS";
        }
        if (normalizedGoal.contains("ENDURANCE") || normalizedActivity.contains("HIGH")) {
            return "ENDURANCE";
        }
        return "MAINTENANCE";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
