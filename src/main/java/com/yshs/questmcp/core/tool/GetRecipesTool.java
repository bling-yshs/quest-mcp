package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.IngredientKind;
import java.util.Locale;

/** Producing and consuming lookups with identical schema and recipe rendering. */
public final class GetRecipesTool implements Tool {
    private final RecipeReader reader;
    private final boolean uses;

    /**
     * Creates one of the two ingredient recipe lookups.
     * @param reader readiness-checked JEI reader
     * @param uses true for get_uses, false for get_recipes
     */
    public GetRecipesTool(RecipeReader reader, boolean uses) {
        this.reader = reader;
        this.uses = uses;
    }

    /** @return the chosen lookup's MCP name */
    @Override
    public String name() {
        return uses ? "get_uses" : "get_recipes";
    }

    /** @return lookup direction, category and paging guidance */
    @Override
    public String description() {
        return "Find JEI recipes " + (uses ? "consuming" : "producing")
                + " an item or fluid across all categories. Returns total and per-category counts, catalysts, "
                + "ingredient amounts and alternatives, tag slots and shaped crafting grids. Kind defaults to item. "
                + "Use category to filter, limit (default 10, max 50) and offset to page. Category-specific drawn "
                + "text such as energy, time and chances is not included.";
    }

    /** @return required ingredient ID, optional kind/category and bounded paging */
    @Override
    public JsonObject inputSchema() {
        JsonObject properties = new JsonObject();
        JsonObject id = new JsonObject();
        id.addProperty("type", "string");
        properties.add("id", id);
        JsonObject kind = new JsonObject();
        kind.addProperty("type", "string");
        JsonArray kinds = new JsonArray();
        kinds.add("item");
        kinds.add("fluid");
        kind.add("enum", kinds);
        kind.addProperty("default", "item");
        properties.add("kind", kind);
        JsonObject category = new JsonObject();
        category.addProperty("type", "string");
        properties.add("category", category);
        Paging.addProperties(properties);
        JsonArray required = new JsonArray();
        required.add("id");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Reads counts and only the requested page of recipe layouts.
     * @param arguments validated identity, kind, category and paging
     * @return compact Markdown recipes
     * @throws ToolException if an identity/category is unknown or JEI is unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String id = arguments.get("id").getAsString().strip();
        IngredientKind kind = arguments.has("kind")
                ? IngredientKind.valueOf(arguments.get("kind").getAsString().toUpperCase(Locale.ROOT))
                : IngredientKind.ITEM;
        String category = arguments.has("category") ? arguments.get("category").getAsString().strip() : "";
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 10;
        int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;
        var page = reader.read(source ->
                source.recipes(id, kind, uses, category, limit, offset))
                .orElseThrow(() -> new ToolException("Unknown ingredient ID `" + id + "` or category `" + category
                        + "`. Use search_items for IDs and list_categories for category UIDs."));
        return RecipeMarkdown.render(page);
    }
}
