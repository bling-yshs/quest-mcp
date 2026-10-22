package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.RecipeSource.Ingredient;
import java.util.List;
import java.util.regex.Pattern;

/** Searches JEI ingredient identities and current-language names. */
public final class SearchItemsTool implements Tool {
    private final RecipeReader reader;

    /**
     * Creates item search.
     * @param reader readiness-checked JEI reader
     */
    public SearchItemsTool(RecipeReader reader) {
        this.reader = reader;
    }

    /** @return the MCP tool name */
    @Override
    public String name() {
        return "search_items";
    }

    /** @return searchable fields and follow-up guidance */
    @Override
    public String description() {
        return "Search JEI items and fluids by literal registry ID, current-language name, "
                + "ignoring case. Returns ID, kind, name and mod. Use get_item_info, get_recipes or get_uses with "
                + "the result's ID and kind. Default limit 10, maximum 50.";
    }

    /** @return required query, paging schema */
    @Override
    public JsonObject inputSchema() {
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        JsonObject properties = new JsonObject();
        properties.add("query", query);
        Paging.addProperties(properties);
        JsonArray required = new JsonArray();
        required.add("query");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Searches the source and renders one page.
     * @param arguments validated query, paging
     * @return Markdown ingredient identities
     * @throws ToolException for a blank query or unavailable JEI
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String query = arguments.get("query").getAsString().strip();
        if (query.isBlank()) {
            throw new ToolException("The query must not be blank. Enter an item name or registry ID.");
        }
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        List<Ingredient> matches = reader.read(source -> source.ingredients().stream()
                .filter(item -> pattern.matcher(item.id()).find() || pattern.matcher(item.name()).find()).toList());
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 10;
        int start = Math.min(arguments.has("offset") ? arguments.get("offset").getAsInt() : 0, matches.size());
        int end = start + Math.min(limit, matches.size() - start);
        StringBuilder out = new StringBuilder();
        Paging.appendHeader(out, matches.size(), start, end);
        if (start == end) {
            return out.append(matches.isEmpty() ? "\nNo matching ingredients.\n" : "\nNo results at this offset.\n")
                    .toString();
        }
        out.append('\n');
        for (Ingredient item : matches.subList(start, end)) {
            out.append("- `").append(item.id()).append("` (").append(item.kind().name().toLowerCase(java.util.Locale.ROOT))
                    .append(") ").append(item.name()).append(" — Mod: `").append(item.mod()).append("`\n");
        }
        return out.toString();
    }
}
