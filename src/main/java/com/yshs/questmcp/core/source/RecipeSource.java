package com.yshs.questmcp.core.source;

import java.util.List;
import java.util.Optional;

/** JEI reads returning neutral data; every method runs on the client game thread. */
public interface RecipeSource {
    /**
     * Lists JEI item and fluid identities with names in the game's current language.
     * @return known ingredients, deduplicated by kind and registry ID
     */
    List<Ingredient> ingredients();

    /**
     * Reads details of a known ingredient.
     * @param id the registry ID
     * @param kind item or fluid
     * @return details, or empty for an unknown identity
     */
    Optional<IngredientInfo> ingredientInfo(String id, IngredientKind kind);

    /**
     * Looks up a page of recipes producing or consuming an identity.
     * @param id registry ID
     * @param kind item or fluid
     * @param uses true for input recipes, false for output recipes
     * @param category optional category UID, or empty for every category
     * @param limit maximum page size
     * @param offset recipes to skip
     * @return page with counts, or empty for an unknown ingredient or category
     */
    Optional<RecipePage> recipes(String id, IngredientKind kind, boolean uses, String category,
            int limit, int offset);

    /**
     * Lists a page of visible JEI categories with catalysts and recipe counts.
     * @param limit page size
     * @param offset categories to skip
     * @return category counts and selected page
     */
    CategoryPage categories(int limit, int offset);

    /**
     * Browses every visible recipe of one category.
     * @param category category UID
     * @param limit page size
     * @param offset recipes to skip
     * @return the recipe page, or empty for an unknown category
     */
    Optional<RecipePage> categoryRecipes(String category, int limit, int offset);

    /**
     * One recipe category and its registered crafting stations.
     * @param id JEI category UID
     * @param title category title
     * @param catalysts registered crafting stations
     * @param recipeCount number of visible recipes
     */
    record Category(String id, String title, List<Amount> catalysts, int recipeCount) {
        /**
         * Copies catalysts for an immutable category snapshot.
         * @param id category UID
         * @param title category title
         * @param catalysts crafting stations
         * @param recipeCount visible recipe count
         */
        public Category {
            catalysts = List.copyOf(catalysts);
        }
    }

    /**
     * A category-list page with a complete count.
     * @param total number of visible categories
     * @param offset requested offset
     * @param categories categories on the page
     */
    record CategoryPage(int total, int offset, List<Category> categories) {
        /**
         * Copies categories for an immutable list snapshot.
         * @param total visible category count
         * @param offset requested offset
         * @param categories the displayed page
         */
        public CategoryPage {
            categories = List.copyOf(categories);
        }
    }

    /** Slot roles exposed by recipe tools. */
    enum Role { INPUT, OUTPUT, CATALYST }

    /**
     * A counted ingredient alternative.
     * @param ingredient item or fluid identity
     * @param amount item count or millibuckets; negative means unspecified by JEI
     */
    record Amount(Ingredient ingredient, long amount) {
    }

    /**
     * A shaped crafting grid position.
     * @param row zero-based row
     * @param column zero-based column
     */
    record GridPosition(int row, int column) {
    }

    /**
     * One ingredient slot, whose alternatives represent a choice.
     * @param role input, output or catalyst
     * @param alternatives the possible ingredient stacks
     * @param tag optional tag ID
     * @param grid optional shaped crafting position
     */
    record Slot(Role role, List<Amount> alternatives, Optional<String> tag, Optional<GridPosition> grid) {
        /**
         * Copies the alternatives for an immutable slot.
         * @param role slot role
         * @param alternatives ingredient choices
         * @param tag optional tag ID
         * @param grid optional grid position
         */
        public Slot {
            alternatives = List.copyOf(alternatives);
        }
    }

    /**
     * A recipe's category and readable ingredient slots.
     * @param categoryId JEI category UID
     * @param categoryTitle category display title
     * @param slots recipe inputs, outputs and catalysts
     */
    record Recipe(String categoryId, String categoryTitle, List<Slot> slots) {
        /**
         * Copies the slots for an immutable recipe.
         * @param categoryId category UID
         * @param categoryTitle category title
         * @param slots readable ingredient slots
         */
        public Recipe {
            slots = List.copyOf(slots);
        }
    }

    /**
     * The count for one category after filtering.
     * @param id category UID
     * @param title category title
     * @param count matching recipe count
     */
    record CategoryCount(String id, String title, int count) {
    }

    /**
     * Counts for the complete lookup plus only the requested page of layouts.
     * @param total all matching recipes
     * @param offset requested offset
     * @param categories counts by category
     * @param recipes recipes on this page
     */
    record RecipePage(int total, int offset, List<CategoryCount> categories, List<Recipe> recipes) {
        /**
         * Copies page collections for a safe game-thread snapshot.
         * @param total matching recipe count
         * @param offset requested offset
         * @param categories complete category counts
         * @param recipes the displayed recipe page
         */
        public RecipePage {
            categories = List.copyOf(categories);
            recipes = List.copyOf(recipes);
        }
    }

    /**
     * One searchable ingredient identity.
     * @param id registry ID
     * @param kind item or fluid
     * @param name current-language display name
     * @param mod the mod ID supplied by JEI
     */
    record Ingredient(String id, IngredientKind kind, String name, String mod) {
    }

    /**
     * Readable ingredient details.
     * @param ingredient its identity and display name
     * @param tooltip plain tooltip lines
     * @param tags registry tag IDs without a leading hash
     * @param information JEI information-page text
     */
    record IngredientInfo(Ingredient ingredient, List<String> tooltip, List<String> tags, List<String> information) {
        /**
         * Copies the detail lists to keep the snapshot immutable.
         * @param ingredient searchable identity
         * @param tooltip plain tooltip lines
         * @param tags tag IDs
         * @param information JEI information-page text
         */
        public IngredientInfo {
            tooltip = List.copyOf(tooltip);
            tags = List.copyOf(tags);
            information = List.copyOf(information);
        }
    }
}
