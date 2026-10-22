package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;

/** Lists visible JEI recipe categories with crafting stations and recipe counts. */
public final class ListCategoriesTool implements Tool {
    private final RecipeReader reader;

    /**
     * Creates category browsing.
     * @param reader readiness-checked JEI reader
     */
    public ListCategoriesTool(RecipeReader reader) {
        this.reader = reader;
    }

    /** @return the MCP tool name */
    @Override
    public String name() {
        return "list_categories";
    }

    /** @return category metadata and follow-up guidance */
    @Override
    public String description() {
        return "List visible JEI recipe categories with UID, title, crafting-station catalysts and recipe count. "
                + "Use get_category_recipes with a UID to see what a machine or crafting category can make. "
                + "Results use the game language and paging (default limit 10, max 50).";
    }

    /** @return bounded paging schema */
    @Override
    public JsonObject inputSchema() {
        JsonObject properties = new JsonObject();
        Paging.addProperties(properties);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Renders one page of categories.
     * @param arguments validated paging arguments
     * @return Markdown category identities and catalysts
     * @throws ToolException if JEI or the world is unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 10;
        int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;
        var page = reader.read(source -> source.categories(limit, offset));
        StringBuilder out = new StringBuilder();
        int start = Math.min(offset, page.total());
        Paging.appendHeader(out, page.total(), start, start + page.categories().size());
        if (page.categories().isEmpty()) {
            return out.append(page.total() == 0 ? "\nNo recipe categories.\n" : "\nNo results at this offset.\n")
                    .toString();
        }
        out.append('\n');
        for (var category : page.categories()) {
            out.append("- `").append(category.id()).append("` ").append(category.title()).append(" — ")
                    .append(category.recipeCount()).append(category.recipeCount() == 1 ? " recipe\n  Catalysts: "
                            : " recipes\n  Catalysts: ");
            if (category.catalysts().isEmpty()) {
                out.append("None.");
            } else {
                RecipeMarkdown.appendAmounts(out, category.catalysts());
            }
            out.append('\n');
        }
        return out.toString();
    }
}
