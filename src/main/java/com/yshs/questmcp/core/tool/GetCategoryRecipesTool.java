package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Browses recipes in one JEI category with the common recipe renderer. */
public final class GetCategoryRecipesTool implements Tool {
    private final RecipeReader reader;

    /**
     * Creates category recipe browsing.
     * @param reader readiness-checked JEI reader
     */
    public GetCategoryRecipesTool(RecipeReader reader) {
        this.reader = reader;
    }

    /** @return the MCP tool name */
    @Override
    public String name() {
        return "get_category_recipes";
    }

    /** @return category selection and rendering guidance */
    @Override
    public String description() {
        return "Browse visible JEI recipes in one category from list_categories. Returns recipes with the same "
                + "amounts, alternatives, tags, catalysts and shaped grids as get_recipes/get_uses. "
                + "Results use the game language, limit (default 10, max 50) and offset.";
    }

    /** @return required category UID and bounded paging schema */
    @Override
    public JsonObject inputSchema() {
        JsonObject category = new JsonObject();
        category.addProperty("type", "string");
        category.addProperty("description", "JEI category UID from list_categories.");
        JsonObject properties = new JsonObject();
        properties.add("category", category);
        Paging.addProperties(properties);
        JsonArray required = new JsonArray();
        required.add("category");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Renders one category's selected recipe page.
     * @param arguments validated category and paging
     * @return common recipe Markdown
     * @throws ToolException if the category is unknown or JEI is unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String category = arguments.get("category").getAsString().strip();
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 10;
        int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;
        var page = reader.read(source -> source.categoryRecipes(category, limit, offset))
                .orElseThrow(() -> new ToolException("Unknown category `" + category
                        + "`. Call list_categories for known category UIDs."));
        return RecipeMarkdown.render(page);
    }
}
