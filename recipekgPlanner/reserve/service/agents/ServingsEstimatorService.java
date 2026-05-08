package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.RecipeCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ServingsEstimatorService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentMap<String, Double> cache = new ConcurrentHashMap<>();

    @Value("${gemini.api-key}")
    private String apiKey;

    public Double estimateServings(RecipeCandidate recipe) {
        if (recipe == null) return null;

        String cacheKey = buildCacheKey(recipe);
        if (cacheKey.isBlank()) return null;

        Double cached = cache.get(cacheKey);
        if (cached != null) return cached;

        String ingredients = buildIngredientsSummary(recipe.getIngredients());
        if (ingredients.isBlank()) return null;

        String prompt = """
You are estimating how many servings a recipe yields.
Return STRICT JSON only with a numeric servings value.

Rules:
- Use reasonable portion sizes.
- If uncertain, return a conservative integer.
- Do NOT include text, units, or extra keys.

Return format:
{"servings": <number>}

RECIPE NAME:
%s

INGREDIENTS:
%s
""".formatted(safeValue(recipe.getLabel(), "(unknown recipe)"), ingredients);

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

        String raw = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node = mapper.readTree(raw);
            String jsonText = node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

            Double servings = parseServingsFromJson(jsonText);
            if (servings != null && servings > 0) {
                cache.put(cacheKey, servings);
                return servings;
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private String buildCacheKey(RecipeCandidate recipe) {
        String uri = safeValue(recipe.getUri(), "");
        if (!uri.isBlank()) return uri;
        return safeValue(recipe.getLabel(), "");
    }

    private String buildIngredientsSummary(List<IngredientUse> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return "";

        List<String> lines = new ArrayList<>();
        for (IngredientUse use : ingredients) {
            String line = formatIngredientLine(use);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        return String.join("\n", lines).trim();
    }

    private String formatIngredientLine(IngredientUse use) {
        if (use == null) return "";

        String qty = safeValue(use.getQuantity(), "");
        String unit = safeValue(use.getUnit(), "");
        String name = safeValue(use.getName(), "");

        StringBuilder line = new StringBuilder();
        if (!qty.isBlank()) line.append(qty).append(" ");
        if (!unit.isBlank()) line.append(unit).append(" ");
        if (!name.isBlank()) line.append(name);

        return line.toString().trim();
    }

    private Double parseServingsFromJson(String jsonText) {
        String trimmed = jsonText == null ? "" : jsonText.trim();
        if (trimmed.isBlank()) return null;

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

        try {
            JsonNode node = mapper.readTree(trimmed);
            JsonNode servingsNode = node.get("servings");
            if (servingsNode == null) return null;

            if (servingsNode.isNumber()) {
                return servingsNode.asDouble();
            }

            if (servingsNode.isTextual()) {
                return parseServingsText(servingsNode.asText());
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private Double parseServingsText(String raw) {
        if (raw == null || raw.isBlank()) return null;

        Matcher matcher = NUMBER_PATTERN.matcher(raw);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            try {
                values.add(Double.parseDouble(matcher.group()));
            } catch (Exception e) {
                // Ignore unparsable values
            }
        }

        if (values.isEmpty()) return null;
        if (values.size() == 1) return values.get(0);

        double left = values.get(0);
        double right = values.get(1);
        if (left > 0 && right > 0) return (left + right) / 2.0;

        return Math.max(left, right);
    }

    private String safeValue(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }
}
