package com.recipekg.planner.response;

import com.recipekg.planner.model.MedicalManifest;
import com.recipekg.planner.model.RecipeCandidate;
import lombok.Getter;

import java.util.List;
@Getter
public class PantryResponse {
    private String sparqlQuery;
    private List<RecipeCandidate> recipes;
    private MedicalManifest manifest;

    public PantryResponse(String sparqlQuery, List<RecipeCandidate> recipes, MedicalManifest manifest) {
        this.sparqlQuery = sparqlQuery;
        this.recipes = recipes;
        this.manifest=manifest;
    }

}
