package com.recipekg.planner.service;

import com.recipekg.planner.model.NutritionPlan;
import com.recipekg.planner.model.NutritionistSelectionState;
import com.recipekg.planner.model.PlannedMeal;
import com.recipekg.planner.model.RecipeCandidate;
import com.recipekg.planner.model.RecipeSubstitution;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class NutritionistBatchSelectionService {

    public List<List<RecipeCandidate>> firstBatches(List<RecipeCandidate> recipes, int batchSize, int maxBatches) {
        if (recipes == null || recipes.isEmpty()) return List.of();

        List<RecipeCandidate> usable = recipes.stream()
                .filter(Objects::nonNull)
                .toList();
        int safeBatchSize = Math.max(1, batchSize);
        int safeMaxBatches = Math.max(1, maxBatches);

        List<List<RecipeCandidate>> batches = new ArrayList<>();
        for (int start = 0; start < usable.size() && batches.size() < safeMaxBatches; start += safeBatchSize) {
            int end = Math.min(start + safeBatchSize, usable.size());
            batches.add(usable.subList(start, end));
        }
        return batches;
    }

    public NutritionistSelectionState normalizeState(
            NutritionistSelectionState rawState,
            Set<String> allowedRecipeIds,
            int maxSelected
    ) {
        if (rawState == null) rawState = NutritionistSelectionState.empty();

        List<String> selected = normalizeIds(rawState.selectedRecipeIds(), allowedRecipeIds, maxSelected);
        List<String> rejected = normalizeIds(rawState.rejectedRecipeIds(), allowedRecipeIds, Integer.MAX_VALUE);
        List<RecipeSubstitution> substitutions = normalizeSubstitutions(rawState.substitutions(), allowedRecipeIds);

        return new NutritionistSelectionState(
                selected,
                rejected,
                substitutions,
                safeStrings(rawState.missingNeeds(), 8),
                safeStrings(rawState.nutritionConcerns(), 8),
                safeStrings(rawState.planningNotes(), 8)
        );
    }

    public List<RecipeCandidate> selectedCandidates(
            NutritionistSelectionState state,
            Map<String, RecipeCandidate> recipeById
    ) {
        if (state == null || state.selectedRecipeIds() == null || recipeById == null) return List.of();

        return state.selectedRecipeIds().stream()
                .map(recipeById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<RecipeCandidate> unusedCandidates(
            Collection<RecipeCandidate> allRecipes,
            NutritionPlan currentPlan,
            int limit
    ) {
        if (allRecipes == null || allRecipes.isEmpty()) return List.of();

        Set<String> usedUris = usedRecipeUris(currentPlan);

        List<RecipeCandidate> unused = new ArrayList<>();
        for (RecipeCandidate recipe : allRecipes) {
            if (recipe == null) continue;
            if (recipe.getUri() != null && usedUris.contains(recipe.getUri())) continue;
            unused.add(recipe);
            if (unused.size() >= Math.max(1, limit)) break;
        }
        return unused;
    }

    private List<String> normalizeIds(List<String> ids, Set<String> allowedRecipeIds, int limit) {
        if (ids == null || ids.isEmpty() || allowedRecipeIds == null || allowedRecipeIds.isEmpty()) return List.of();

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            String trimmed = id.trim();
            if (!allowedRecipeIds.contains(trimmed)) continue;
            normalized.add(trimmed);
            if (normalized.size() >= limit) break;
        }
        return List.copyOf(normalized);
    }

    private List<RecipeSubstitution> normalizeSubstitutions(
            List<RecipeSubstitution> substitutions,
            Set<String> allowedRecipeIds
    ) {
        if (substitutions == null || substitutions.isEmpty()) return List.of();

        List<RecipeSubstitution> normalized = new ArrayList<>();
        for (RecipeSubstitution substitution : substitutions) {
            if (substitution == null) continue;
            String removed = safeId(substitution.removedRecipeId());
            String added = safeId(substitution.addedRecipeId());
            if (!allowedRecipeIds.contains(removed) || !allowedRecipeIds.contains(added)) continue;
            normalized.add(new RecipeSubstitution(removed, added, safe(substitution.reason())));
        }
        return normalized;
    }

    private List<String> safeStrings(List<String> values, int limit) {
        if (values == null || values.isEmpty()) return List.of();

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(limit)
                .toList();
    }

    private Set<String> usedRecipeUris(NutritionPlan currentPlan) {
        Set<String> used = new LinkedHashSet<>();
        if (currentPlan == null || currentPlan.days() == null) return used;

        currentPlan.days().forEach(day -> {
            if (day.meals() == null) return;
            for (PlannedMeal meal : day.meals()) {
                if (meal.recipeUri() != null) used.add(meal.recipeUri());
            }
        });
        return used;
    }

    private String safeId(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
