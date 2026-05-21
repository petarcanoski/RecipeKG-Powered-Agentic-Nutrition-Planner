package com.recipekg.planner.controller;

import com.recipekg.planner.model.DailyMealPlan;
import com.recipekg.planner.model.IngredientUse;
import com.recipekg.planner.model.MacroSummary;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.PlannedMeal;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.User;
import com.recipekg.planner.model.UserProfile;
import com.recipekg.planner.repository.UserProfileRepository;
import com.recipekg.planner.response.FrontendNutritionPlanResponse;
import com.recipekg.planner.response.PantryResponse;
import com.recipekg.planner.service.FoodScientistService;
import com.recipekg.planner.service.NutritionPlanPersistenceService;
import com.recipekg.planner.service.NutritionPlanResponseMapper;
import com.recipekg.planner.service.agents.AgentOrchestratorService;
import com.recipekg.planner.service.agents.MedicalAgentService;
import com.recipekg.planner.service.agents.ProgressAgentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class TestController {

    private final MedicalAgentService medicalAgentService;
    private final UserProfileRepository userProfileRepository;
    private final FoodScientistService foodScientistService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final ProgressAgentService progressAgentService;
    private final NutritionPlanResponseMapper nutritionPlanResponseMapper;
    private final NutritionPlanPersistenceService nutritionPlanPersistenceService;


    public TestController(MedicalAgentService medicalAgentService, UserProfileRepository userProfileRepository, FoodScientistService foodScientistService, AgentOrchestratorService agentOrchestratorService, ProgressAgentService progressAgentService, NutritionPlanResponseMapper nutritionPlanResponseMapper, NutritionPlanPersistenceService nutritionPlanPersistenceService) {
        this.medicalAgentService = medicalAgentService;
        this.userProfileRepository = userProfileRepository;
        this.foodScientistService = foodScientistService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.progressAgentService = progressAgentService;
        this.nutritionPlanResponseMapper = nutritionPlanResponseMapper;
        this.nutritionPlanPersistenceService = nutritionPlanPersistenceService;
    }

    @Value("${gemini.api-key}")
    private String apiKey;

    @GetMapping("/test-gemini")
    @ResponseBody
    public String testGemini() {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey;

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> part = new HashMap<>();
        part.put("text", "Explain recursion in one sentence");

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            List candidates = (List) response.getBody().get("candidates");
            Map firstCandidate = (Map) candidates.get(0);
            Map contentMap = (Map) firstCandidate.get("content");
            List parts = (List) contentMap.get("parts");
            Map textPart = (Map) parts.get(0);

            return (String) textPart.get("text");

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/test-medical/{id}")
    public FrontendNutritionPlanResponse testMedicalAgent(@PathVariable Long id){
        return hardcodedNutritionPlan();
    }

    @GetMapping({"/test-nutrition-plan", "/test-nutrition-plan/{userId}"})
    public FrontendNutritionPlanResponse testNutritionPlan(@PathVariable(required = false) Long userId){
        FrontendNutritionPlanResponse plan = hardcodedNutritionPlan();

        if (userId != null) {
            UserProfile profile = userProfileRepository.findByUserId(userId)
                    .orElseThrow();
            User user = profile.getUser();
            int weekNumber = resolveWeekNumber(user);
            nutritionPlanPersistenceService.save(
                    user,
                    weekNumber,
                    resolveStartDate(user, weekNumber),
                    plan,
                    NutritionPlanPersistenceService.TEST_MOCK
            );
        }

        return plan;
    }

    @GetMapping("/test-full-plan/{id}")
    public PantryResponse testFullPlan(@PathVariable Long id){

        UserProfile userProfile=userProfileRepository.findByUserId(id).get();
//        MedicalManifest m= medicalAgentService.generateMedicalAdvice(userProfile);
//        return foodScientistService.buildSafeCandidateQuery(m);
        return agentOrchestratorService.generateFullPlan(userProfile);
    }

    private FrontendNutritionPlanResponse hardcodedNutritionPlan() {
        List<RecipeCandidate> recipes = List.of(
                recipe(
                        "mock://recipe/berry-oats",
                        "Berry Overnight Oats",
                        List.of(
                                ingredient("80", "g", "rolled oats"),
                                ingredient("180", "g", "Greek yogurt"),
                                ingredient("120", "g", "blueberries"),
                                ingredient("15", "g", "chia seeds"),
                                ingredient("10", "g", "honey")
                        )
                ),
                recipe(
                        "mock://recipe/chicken-rice-bowl",
                        "Chicken Rice Bowl",
                        List.of(
                                ingredient("180", "g", "chicken breast"),
                                ingredient("220", "g", "cooked rice"),
                                ingredient("120", "g", "broccoli"),
                                ingredient("10", "g", "olive oil"),
                                ingredient("1", "pinch", "salt")
                        )
                ),
                recipe(
                        "mock://recipe/salmon-potato",
                        "Salmon Sweet Potato Plate",
                        List.of(
                                ingredient("160", "g", "salmon fillet"),
                                ingredient("250", "g", "sweet potato"),
                                ingredient("100", "g", "green beans"),
                                ingredient("8", "g", "olive oil"),
                                ingredient("1", "tbsp", "lemon juice")
                        )
                ),
                recipe(
                        "mock://recipe/turkey-chili",
                        "Turkey Bean Chili",
                        List.of(
                                ingredient("170", "g", "ground turkey"),
                                ingredient("160", "g", "kidney beans"),
                                ingredient("140", "g", "crushed tomatoes"),
                                ingredient("80", "g", "corn"),
                                ingredient("2", "tsp", "chili powder")
                        )
                ),
                recipe(
                        "mock://recipe/tofu-stir-fry",
                        "Tofu Noodle Stir Fry",
                        List.of(
                                ingredient("180", "g", "firm tofu"),
                                ingredient("220", "g", "rice noodles"),
                                ingredient("120", "g", "mixed vegetables"),
                                ingredient("12", "g", "sesame oil"),
                                ingredient("1", "tbsp", "soy sauce")
                        )
                ),
                recipe(
                        "mock://recipe/beef-pasta",
                        "Beef Tomato Pasta",
                        List.of(
                                ingredient("160", "g", "lean beef"),
                                ingredient("240", "g", "cooked pasta"),
                                ingredient("150", "g", "tomato sauce"),
                                ingredient("20", "g", "parmesan"),
                                ingredient("5", "g", "olive oil")
                        )
                ),
                recipe(
                        "mock://recipe-cottage-cheese-toast",
                        "Cottage Cheese Toast",
                        List.of(
                                ingredient("2", "slices", "whole grain bread"),
                                ingredient("160", "g", "cottage cheese"),
                                ingredient("80", "g", "tomato"),
                                ingredient("30", "g", "avocado"),
                                ingredient("1", "pinch", "black pepper")
                        )
                )
        );

        List<DailyMealPlan> days = List.of(
                day(1, List.of(
                        meal("breakfast", "M1", "mock://recipe/berry-oats", "Berry Overnight Oats", 1.0, new MacroSummary(620, 38, 82, 16, 24, 210)),
                        meal("lunch", "M2", "mock://recipe/chicken-rice-bowl", "Chicken Rice Bowl", 1.0, new MacroSummary(760, 58, 86, 18, 6, 680)),
                        meal("dinner", "M3", "mock://recipe/salmon-potato", "Salmon Sweet Potato Plate", 1.0, new MacroSummary(710, 44, 72, 25, 12, 430))
                )),
                day(2, List.of(
                        meal("breakfast", "M4", "mock://recipe-cottage-cheese-toast", "Cottage Cheese Toast", 1.0, new MacroSummary(520, 35, 54, 18, 9, 540)),
                        meal("lunch", "M5", "mock://recipe/turkey-chili", "Turkey Bean Chili", 1.2, new MacroSummary(780, 62, 78, 22, 11, 760)),
                        meal("dinner", "M6", "mock://recipe/tofu-stir-fry", "Tofu Noodle Stir Fry", 1.0, new MacroSummary(690, 34, 88, 21, 8, 720))
                )),
                day(3, List.of(
                        meal("breakfast", "M7", "mock://recipe/berry-oats", "Berry Overnight Oats", 1.0, new MacroSummary(620, 38, 82, 16, 24, 210)),
                        meal("lunch", "M8", "mock://recipe/beef-pasta", "Beef Tomato Pasta", 1.0, new MacroSummary(830, 52, 96, 24, 12, 690)),
                        meal("dinner", "M9", "mock://recipe/chicken-rice-bowl", "Chicken Rice Bowl", 1.0, new MacroSummary(760, 58, 86, 18, 6, 680))
                )),
                day(4, List.of(
                        meal("breakfast", "M10", "mock://recipe-cottage-cheese-toast", "Cottage Cheese Toast", 1.0, new MacroSummary(520, 35, 54, 18, 9, 540)),
                        meal("lunch", "M11", "mock://recipe/salmon-potato", "Salmon Sweet Potato Plate", 1.0, new MacroSummary(710, 44, 72, 25, 12, 430)),
                        meal("dinner", "M12", "mock://recipe/turkey-chili", "Turkey Bean Chili", 1.2, new MacroSummary(780, 62, 78, 22, 11, 760))
                )),
                day(5, List.of(
                        meal("breakfast", "M13", "mock://recipe/berry-oats", "Berry Overnight Oats", 1.0, new MacroSummary(620, 38, 82, 16, 24, 210)),
                        meal("lunch", "M14", "mock://recipe/tofu-stir-fry", "Tofu Noodle Stir Fry", 1.0, new MacroSummary(690, 34, 88, 21, 8, 720)),
                        meal("dinner", "M15", "mock://recipe/beef-pasta", "Beef Tomato Pasta", 1.0, new MacroSummary(830, 52, 96, 24, 12, 690))
                )),
                day(6, List.of(
                        meal("breakfast", "M16", "mock://recipe-cottage-cheese-toast", "Cottage Cheese Toast", 1.0, new MacroSummary(520, 35, 54, 18, 9, 540)),
                        meal("lunch", "M17", "mock://recipe/chicken-rice-bowl", "Chicken Rice Bowl", 1.0, new MacroSummary(760, 58, 86, 18, 6, 680)),
                        meal("dinner", "M18", "mock://recipe/salmon-potato", "Salmon Sweet Potato Plate", 1.0, new MacroSummary(710, 44, 72, 25, 12, 430))
                )),
                day(7, List.of(
                        meal("breakfast", "M19", "mock://recipe/berry-oats", "Berry Overnight Oats", 1.0, new MacroSummary(620, 38, 82, 16, 24, 210)),
                        meal("lunch", "M20", "mock://recipe/turkey-chili", "Turkey Bean Chili", 1.2, new MacroSummary(780, 62, 78, 22, 11, 760)),
                        meal("dinner", "M21", "mock://recipe/beef-pasta", "Beef Tomato Pasta", 1.0, new MacroSummary(830, 52, 96, 24, 12, 690))
                ))
        );

        MacroSummary weeklyTotals = days.stream()
                .map(DailyMealPlan::estimatedTotals)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        NutritionPlan plan = new NutritionPlan(
                "READY",
                "Hardcoded frontend nutrition plan. No Gemini request was made.",
                days,
                weeklyTotals,
                null
        );

        return nutritionPlanResponseMapper.toFrontend(plan, recipes);
    }

    private DailyMealPlan day(int day, List<PlannedMeal> meals) {
        MacroSummary totals = meals.stream()
                .map(PlannedMeal::estimatedMacros)
                .reduce(MacroSummary.zero(), MacroSummary::plus);

        return new DailyMealPlan(day, meals, totals, "Hardcoded day for frontend development.");
    }

    private PlannedMeal meal(
            String slot,
            String recipeId,
            String recipeUri,
            String recipeLabel,
            double servings,
            MacroSummary macros
    ) {
        return new PlannedMeal(
                slot,
                recipeId,
                recipeUri,
                recipeLabel,
                servings,
                macros,
                "Mock meal used for frontend development."
        );
    }

    private RecipeCandidate recipe(String uri, String label, List<IngredientUse> ingredients) {
        RecipeCandidate recipe = new RecipeCandidate(uri, label, List.of());
        recipe.setIngredients(ingredients);
        return recipe;
    }

    private IngredientUse ingredient(String quantity, String unit, String name) {
        return new IngredientUse(name, quantity, unit, null);
    }

    private int resolveWeekNumber(User user) {
        Integer currentWeek = user.getCurrentWeek();
        return currentWeek == null || currentWeek < 1 ? 1 : currentWeek;
    }

    private LocalDate resolveStartDate(User user, int weekNumber) {
        return Objects.requireNonNullElse(user.getProgramStartDate(), LocalDate.now())
                .plusWeeks(Math.max(weekNumber - 1, 0));
    }

}
