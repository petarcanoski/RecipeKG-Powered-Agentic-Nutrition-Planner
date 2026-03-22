package com.recipekg.planner.service.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipekg.planner.dto.MealDTO;
import com.recipekg.planner.dto.NutritionFacts;
import com.recipekg.planner.dto.UriWeekPlan;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NutritionAgentService {

    private final WebClient webClient;
    private final MacroCalculatorService macroCalculatorService;
    private final RecipeKGService recipeKGService;
    private final SparqlResultParserService sparqlResultParserService;
    private final MealValidatorService mealValidatorService;
    private final ProgressBrainService progressBrain;
    private final MacroBrainService macroBrainService;
    private final VarietyBrainService varietyBrainService;
    private final DiseaseBrainService diseaseBrain;

    @Value("${gemini.api-key}")
    private String apiKey;

    private String extractName(String uri) {
        return uri.substring(uri.lastIndexOf("-") + 1)
                .replace("%20", " ");
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

        String raw = webClient.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            ObjectMapper mapper = new ObjectMapper();

            var node = mapper.readTree(raw);

            return node.get("candidates")
                    .get(0)
                    .get("content")
                    .get("parts")
                    .get(0)
                    .get("text")
                    .asText();

        } catch (Exception e) {
            throw new RuntimeException("Nutrition agent parse failed", e);
        }
    }

    public String generateNutritionPlan(UserProfile profile, String medicalJson) {

        MacroCalculatorService.MacroTargets m =
                macroCalculatorService.calculate(profile);

        String kgRaw =
                recipeKGService.fetchMealsByGoal(profile.getGoal());

        List<MealDTO> meals =
                sparqlResultParserService.parseMeals(kgRaw);

        if (meals.isEmpty()) {
            throw new RuntimeException("KG returned no meals");
        }

        System.out.println("====== PARSED KG MEALS ======");
        meals.forEach(System.out::println);
        System.out.println("=============================");


        String mealList =
                meals.stream()
                        .map(MealDTO::getMealUri)
                        .distinct()
                        .reduce("", (a,b) -> a + "\n" + b);



        String prompt = """
You are a professional sports nutritionist.

Create WEEKLY nutrition strategy.

Rules:
- Respect medical recommendations
- Respect allergies strictly
- Adjust calories to goal
- Return STRICT JSON only
- No markdown
- No explanations

PROFILE:
Age: %d
Weight: %.1f
Goal: %s
ActivityLevel: %s
Diseases: %s
Allergies: %s
Daily calories target: %d
Protein grams: %d
Carbs grams: %d
Fats grams: %d

MEDICAL_ADVICE:
%s

You MUST choose meals ONLY by URI.

STRICT RULES:
- breakfast MUST be a meal URI
- lunch MUST be a meal URI
- dinner MUST be a meal URI
- NEVER invent meals
- NEVER write descriptions
- NEVER write ingredients
- ONLY use URIs from the list

Return STRICT JSON:

{
 "weekPlan":[
   {
     "day":"MONDAY",
     "breakfast":"<URI>",
     "lunch":"<URI>",
     "dinner":"<URI>"
   }
 ]
}

AVAILABLE MEAL URIS:
%s
""".formatted(
                profile.getAge(),
                profile.getWeight(),
                profile.getGoal(),
                profile.getActivityLevel(),
                profile.getDiseases(),
                profile.getAllergies(),
                m.getCalories(),
                m.getProtein(),
                m.getCarbs(),
                m.getFats(),
                medicalJson,
                mealList
        );

        String result = "";
        String feedback = "";

        for (int i = 0; i < 3; i++) {

            String adaptivePrompt = prompt + "\n\nVALIDATION_FEEDBACK:\n" + feedback;

            result = callGemini(adaptivePrompt);

            try {

                ObjectMapper mapper = new ObjectMapper();

                UriWeekPlan plan =
                        mapper.readValue(result, UriWeekPlan.class);

                boolean ok =
                        mealValidatorService.validate(plan, meals);

                if (!ok) {

                    feedback = "INVALID: You must ONLY use provided meal URIs";

                } else {

                    // ⭐ HERE MACRO BRAIN STARTS

                    NutritionFacts weekFacts =
                            macroBrainService.computeWeekMacros(plan, meals);


                    if (!varietyBrainService.isVaried(plan)) {

                        feedback = "Too much repetition. Increase meal variety.";
                        continue;
                    }

                    if (!diseaseBrain.isSafe(plan, meals, profile.getDiseases())) {

                        feedback = "Medical risk detected. Choose safer meals.";
                        continue;
                    }


                    double targetProteinWeek = m.getProtein() * 7;


                    if (weekFacts.getProtein() < targetProteinWeek) {

                        feedback = "Protein too low. Choose more high-protein meals from URI list.";

                    } else {

                        feedback = "OK";
                    }
                }

            } catch (Exception e) {

                feedback = "INVALID JSON STRUCTURE. Return STRICT JSON.";
            }

            System.out.println("AI TRY = " + (i + 1));
            System.out.println("VALIDATION = " + feedback);

            if (feedback.equals("OK")) {
                break;
            }
        }

        return result;
    }
}