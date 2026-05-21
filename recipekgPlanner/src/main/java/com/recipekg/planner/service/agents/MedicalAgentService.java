package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class MedicalAgentService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.retry-503-attempts:5}")
    private int retry503Attempts;

    @Value("${gemini.retry-base-delay-ms:10000}")
    private long retryBaseDelayMillis;

    @Value("${gemini.retry-max-delay-ms:90000}")
    private long retryMaxDelayMillis;

    public MedicalAgentService(WebClient webClient) {
        this.webClient = webClient;
    }


    public MedicalManifest generateMedicalAdvice(UserProfile profile) {

        String prompt = """
        You are an expert Clinical Dietitian and Medical AI Agent.
        Your task is to analyze the user's biological profile and generate a STRICT JSON manifest of dietary constraints.
        
        CRITICAL RULES FOR TERMINOLOGY & STATUS:
        1. STATUS DETERMINATION: 
           - If 'Allergies' is "None" AND 'Diseases/Conditions' is "None", you MUST set "status": "UNCONSTRAINED". 
           - ONLY set "status": "CONSTRAINED" if the user has a specific allergy, disease, or clinical condition requiring strict filtering.
        2. 'hard_exclusions': Your job is to act as a Keyword Unroller. You must translate the user's dietary restrictions into a flat JSON array of singular, root ingredient keywords and common foods that contain them.
                - Do NOT use broad ontology categories. Unroll them into specific, high-risk ingredients.
                - Strip pluralization (e.g., use "peanut" not "peanuts").
                
                EXAMPLES:
                - User: "I have a fish allergy." -> Output: ["fish", "salmon", "tuna", "cod", "trout"]
                - User: "I have a dairy allergy" -> Output: ["milk", "cheese", "butter", "whey"]
                - Uer: "I have a gluten allergy" -> Output: ["wheat", "bread", "flour", "pasta", "crouton", "pretzel"]
                
        
        3. 'nutrient_caps' RULES:
                - Each entry must contain exactly one USDA nutrient name.
                - Do NOT combine nutrients (no commas, no "and", no grouping).
                - Do NOT repeat the same nutrient more than once.
                - Use canonical USDA names only (examples: "Carbs", "Sugar",
                "Sodium", "Energy", "Protein", "Fat").
        4. IF UNCONSTRAINED: Leave the 'hard_exclusions', 'nutrient_caps', and 'required_boosts' arrays completely EMPTY.
        
        USER PROFILE:
        Age: %d
        Gender: %s
        Height (cm): %.1f
        Weight (kg): %.1f
        Blood Type: %s
        Activity Level: %s
        Goal: %s
        Allergies: %s
        Diseases/Conditions: %s
        
        EXPECTED JSON SCHEMA:
        {
          "status": "CONSTRAINED",
          "constraints": {
            "hard_exclusions": ["<String>"],
            "nutrient_caps": [
              { "nutrient": "<String>", "max_value": <Number>, "unit": "<String>" }
            ]
          },
          "medical_rationale": "<String detailing the clinical reasoning>"
        }
        """.formatted(
                profile.getAge(),
                profile.getGender(),
                profile.getHeight(),
                profile.getWeight(),
                profile.getBloodType(),
                profile.getActivityLevel(),
                profile.getGoal(),
                profile.getAllergies(),
                profile.getDiseases()
        );

        Map<String, Object> body = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                },
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        String raw = callGemini(body);

        try {
            JsonNode node = mapper.readTree(raw);
            String jsonText = node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

            return parseMedicalManifest(jsonText);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response into MedicalManifest", e);
        }
    }

        private String callGemini(Map<String, Object> body) {
                int maxAttempts = Math.max(1, retry503Attempts + 1);
                RuntimeException lastError = null;

                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                        try {
                                return callGeminiOnce(body);
                        } catch (WebClientResponseException e) {
                                lastError = e;
                                if (!isRetryableGeminiStatus(e) || attempt >= maxAttempts) break;

                                retryAfterDelay(
                                        "Medical agent Gemini call returned " + e.getStatusCode().value() + " " + e.getStatusText(),
                                        attempt,
                                        maxAttempts
                                );
                        } catch (WebClientRequestException e) {
                                lastError = e;
                                if (attempt >= maxAttempts) break;

                                retryAfterDelay("Medical agent Gemini request failed before receiving a response: " + rootCauseMessage(e), attempt, maxAttempts);
                        }
                }

                throw lastError == null ? new RuntimeException("Medical agent Gemini request failed") : lastError;
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
                        throw new RuntimeException("Interrupted while waiting to retry Medical agent Gemini request", e);
                }
        }

        private MedicalManifest parseMedicalManifest(String jsonText) throws Exception {
                String trimmed = jsonText == null ? "" : jsonText.trim();

                if (trimmed.startsWith("```")) {
                        int firstLineEnd = trimmed.indexOf('\n');
                        if (firstLineEnd >= 0) {
                                trimmed = trimmed.substring(firstLineEnd + 1);
                        }
                        int lastFence = trimmed.lastIndexOf("```");
                        if (lastFence >= 0) {
                                trimmed = trimmed.substring(0, lastFence);
                        }
                        trimmed = trimmed.trim();
                }

                if (trimmed.startsWith("[")) {
                        JsonNode arrayNode = mapper.readTree(trimmed);
                        if (arrayNode.isArray() && arrayNode.size() > 0) {
                                return mapper.treeToValue(arrayNode.get(0), MedicalManifest.class);
                        }
                }

                return mapper.readValue(trimmed, MedicalManifest.class);
        }
}
