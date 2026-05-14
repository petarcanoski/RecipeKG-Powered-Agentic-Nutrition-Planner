package com.recipekg.planner.response;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.PerformanceManifest;
import com.recipekg.planner.model.RecipeCandidate;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class PantryResponse {
    private String sparqlQuery;
    private List<RecipeCandidate> recipes;
    private int recipesLen;
    private MedicalManifest manifest;
    private PerformanceManifest performanceManifest;
    private NutritionPlan nutritionPlan;


    public PantryResponse(String sparqlQuery, List<RecipeCandidate> recipes, MedicalManifest manifest, PerformanceManifest performanceManifest) {
        this.sparqlQuery = sparqlQuery;
        this.recipes = recipes;
        this.manifest = manifest;
        this.performanceManifest = performanceManifest;
        this.recipesLen=recipes.size();
    }

}
