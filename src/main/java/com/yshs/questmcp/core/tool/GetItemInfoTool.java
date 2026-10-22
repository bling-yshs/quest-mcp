package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.IngredientKind;
import java.util.Locale;

/** Reads JEI ingredient tooltips, tags and information pages. */
public final class GetItemInfoTool implements Tool {
    private final RecipeReader reader;

    /**
     * Creates ingredient details.
     * @param reader readiness-checked JEI reader
     */
    public GetItemInfoTool(RecipeReader reader) {
        this.reader = reader;
    }

    /** @return the MCP tool name */
    @Override
    public String name() {
        return "get_item_info";
    }

    /** @return details available and identity guidance */
    @Override
    public String description() {
        return "Read an item's or fluid's name, mod, plain tooltip, tags and JEI information-page text. "
                + "Pass a registry ID from search_items; kind defaults to item. "
                + "Names and text use the current game language; literal mod-provided text stays as supplied.";
    }

    /** @return ingredient identity schema */
    @Override
    public JsonObject inputSchema() {
        JsonObject id = new JsonObject();
        id.addProperty("type", "string");
        JsonObject kind = new JsonObject();
        kind.addProperty("type", "string");
        JsonArray kinds = new JsonArray();
        kinds.add("item");
        kinds.add("fluid");
        kind.add("enum", kinds);
        kind.addProperty("default", "item");
        JsonObject properties = new JsonObject();
        properties.add("id", id);
        properties.add("kind", kind);
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
     * Renders ingredient details without game types.
     * @param arguments validated registry ID, kind
     * @return Markdown details
     * @throws ToolException if the ingredient is unknown or JEI is unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String id = arguments.get("id").getAsString().strip();
        IngredientKind kind = arguments.has("kind")
                ? IngredientKind.valueOf(arguments.get("kind").getAsString().toUpperCase(Locale.ROOT))
                : IngredientKind.ITEM;
        var info = reader.read(source -> source.ingredientInfo(id, kind))
                .orElseThrow(() -> new ToolException("Unknown " + kind.name().toLowerCase(Locale.ROOT) + " ID `"
                        + id + "`. Use search_items to find a known ID and kind."));
        var item = info.ingredient();
        StringBuilder out = new StringBuilder("# ").append(item.name()).append(" (`").append(item.id())
                .append("`)\n\n- Kind: ").append(kind.name().toLowerCase(Locale.ROOT)).append("\n- Mod: `")
                .append(item.mod()).append("`\n\n## Tooltip\n\n");
        out.append(info.tooltip().isEmpty() ? "None.\n" : String.join("\n", info.tooltip()) + "\n");
        out.append("\n## Tags\n\n");
        if (info.tags().isEmpty()) {
            out.append("None.\n");
        } else {
            info.tags().forEach(tag -> out.append("- `#").append(tag).append("`\n"));
        }
        out.append("\n## JEI information\n\n")
                .append(info.information().isEmpty() ? "None.\n" : String.join("\n\n", info.information()) + "\n");
        return out.toString();
    }
}
