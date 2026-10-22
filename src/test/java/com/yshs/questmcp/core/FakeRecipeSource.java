package com.yshs.questmcp.core;

import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.RecipeSource;
import java.util.List;
import java.util.Optional;

/** In-memory JEI data used by the real endpoint tests. */
final class FakeRecipeSource implements RecipeSource {
    List<IngredientInfo> entries = List.of();
    List<Recipe> recipeEntries = List.of();

    /**
     * Lists the fake identities with their current display names.
     * @return ingredient snapshots
     */
    @Override
    public List<Ingredient> ingredients() {
        return entries.stream().map(IngredientInfo::ingredient).toList();
    }

    /**
     * Filters and pages fake input/output recipes with complete category counts.
     * @param id ingredient registry ID
     * @param kind requested kind
     * @param uses true for input matching
     * @param category optional category filter
     * @param limit page size
     * @param offset entries to skip
     * @return the page, or empty for unknown identities
     */
    @Override
    public Optional<RecipePage> recipes(String id, IngredientKind kind, boolean uses, String category,
            int limit, int offset) {
        if (ingredientInfo(id, kind).isEmpty()
                || !category.isEmpty() && recipeEntries.stream().noneMatch(recipe -> recipe.categoryId().equals(category))) {
            return Optional.empty();
        }
        List<Recipe> matches = recipeEntries.stream()
                .filter(recipe -> category.isEmpty() || recipe.categoryId().equals(category))
                .filter(recipe -> recipe.slots().stream().filter(slot -> slot.role() == (uses ? Role.INPUT : Role.OUTPUT))
                        .flatMap(slot -> slot.alternatives().stream())
                        .anyMatch(amount -> amount.ingredient().id().equals(id) && amount.ingredient().kind() == kind))
                .toList();
        java.util.LinkedHashMap<String, CategoryCount> counts = new java.util.LinkedHashMap<>();
        for (Recipe recipe : matches) {
            CategoryCount old = counts.get(recipe.categoryId());
            counts.put(recipe.categoryId(), new CategoryCount(recipe.categoryId(), recipe.categoryTitle(),
                    old == null ? 1 : old.count() + 1));
        }
        int start = Math.min(offset, matches.size());
        int end = start + Math.min(limit, matches.size() - start);
        return Optional.of(new RecipePage(matches.size(), offset, List.copyOf(counts.values()),
                matches.subList(start, end)));
    }

    /**
     * Lists fake categories with catalysts and counts.
     * @param limit page size
     * @param offset categories to skip
     * @return the category page
     */
    @Override
    public CategoryPage categories(int limit, int offset) {
        java.util.LinkedHashMap<String, List<Recipe>> grouped = new java.util.LinkedHashMap<>();
        for (Recipe recipe : recipeEntries) {
            grouped.computeIfAbsent(recipe.categoryId(), ignored -> new java.util.ArrayList<>()).add(recipe);
        }
        List<Category> all = grouped.entrySet().stream().map(entry -> new Category(entry.getKey(),
                entry.getValue().getFirst().categoryTitle(), entry.getValue().stream().flatMap(recipe -> recipe.slots().stream())
                        .filter(slot -> slot.role() == Role.CATALYST).flatMap(slot -> slot.alternatives().stream())
                        .distinct().toList(), entry.getValue().size())).toList();
        int start = Math.min(offset, all.size());
        return new CategoryPage(all.size(), offset, all.subList(start, start + Math.min(limit, all.size() - start)));
    }

    /**
     * Pages every fake recipe in a category.
     * @param category category UID
     * @param limit page size
     * @param offset recipes to skip
     * @return the recipe page or empty for unknown categories
     */
    @Override
    public Optional<RecipePage> categoryRecipes(String category, int limit, int offset) {
        List<Recipe> matches = recipeEntries.stream().filter(recipe -> recipe.categoryId().equals(category)).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int start = Math.min(offset, matches.size());
        return Optional.of(new RecipePage(matches.size(), offset,
                List.of(new CategoryCount(category, matches.getFirst().categoryTitle(), matches.size())),
                matches.subList(start, start + Math.min(limit, matches.size() - start))));
    }

    /**
     * Looks up fake details by kind and ID.
     * @param id registry ID
     * @param kind requested kind
     * @return details if found
     */
    @Override
    public Optional<IngredientInfo> ingredientInfo(String id, IngredientKind kind) {
        return entries.stream().filter(info -> info.ingredient().id().equals(id) && info.ingredient().kind() == kind)
                .findFirst();
    }
}
