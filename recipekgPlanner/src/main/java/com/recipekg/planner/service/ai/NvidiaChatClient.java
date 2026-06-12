package com.recipekg.planner.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class NvidiaChatClient {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${nvidia.api-key:}")
    private String apiKey;

    @Value("${nvidia.base-url:https://integrate.api.nvidia.com/v1}")
    private String baseUrl;

    @Value("${nvidia.model:nvidia/llama-3.3-nemotron-super-49b-v1.5}")
    private String defaultModel;

    @Value("${nvidia.temperature:0.6}")
    private double temperature;

    @Value("${nvidia.top-p:0.95}")
    private double topP;

    @Value("${nvidia.max-tokens:65536}")
    private int maxTokens;

    @Value("${nvidia.retry-attempts:5}")
    private int retryAttempts;

    @Value("${nvidia.retry-base-delay-ms:10000}")
    private long retryBaseDelayMillis;

    @Value("${nvidia.retry-max-delay-ms:90000}")
    private long retryMaxDelayMillis;

    public NvidiaChatClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public String complete(String prompt) {
        return complete(prompt, defaultModel);
    }

    public String complete(String prompt, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NVIDIA API key is not configured. Set nvidia.api-key or NVIDIA_API_KEY.");
        }

        String modelId = model == null || model.isBlank() ? defaultModel : model.trim();
        int maxAttempts = Math.max(1, retryAttempts + 1);
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return completeOnce(prompt, modelId);
            } catch (WebClientResponseException e) {
                lastError = e;
                if (!isRetryableStatus(e) || attempt >= maxAttempts) break;

                retryAfterDelay(
                        "NVIDIA model " + modelId + " returned " + e.getStatusCode().value() + " " + e.getStatusText(),
                        attempt,
                        maxAttempts
                );
            } catch (WebClientRequestException e) {
                lastError = e;
                if (attempt >= maxAttempts) break;

                retryAfterDelay("NVIDIA request failed before receiving a response: " + rootCauseMessage(e), attempt, maxAttempts);
            }
        }

        throw lastError == null ? new RuntimeException("NVIDIA request failed") : lastError;
    }

    public String defaultModel() {
        return defaultModel;
    }

    private String completeOnce(String prompt, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt)),
                "temperature", temperature,
                "top_p", topP,
                "max_tokens", maxTokens,
                "frequency_penalty", 0,
                "presence_penalty", 0,
                "stream", false
        );

        String raw = webClient.post()
                .uri(normalizedBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node = mapper.readTree(raw);
            JsonNode content = node.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalStateException("Missing choices[0].message.content");
            }
            return content.asText().trim();
        } catch (Exception e) {
            throw new RuntimeException("NVIDIA response extraction failed", e);
        }
    }

    private String normalizedBaseUrl() {
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? "https://integrate.api.nvidia.com/v1"
                : baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private boolean isRetryableStatus(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 429 || status >= 500;
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

    private long retryDelayMillis(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 4);
        long delay = retryBaseDelayMillis * multiplier;
        return Math.min(delay, retryMaxDelayMillis);
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry NVIDIA request", e);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
