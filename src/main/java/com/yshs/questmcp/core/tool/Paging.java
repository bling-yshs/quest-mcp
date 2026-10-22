package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;

/** Shared paging schema and range rendering for list tools. */
final class Paging {
    /** Prevents construction of the schema utilities. */
    private Paging() {
    }

    /**
     * Adds bounded limit and offset arguments.
     * @param properties the tool's schema properties
     */
    static void addProperties(JsonObject properties) {
        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("minimum", 1);
        limit.addProperty("maximum", 50);
        limit.addProperty("default", 10);
        JsonObject offset = new JsonObject();
        offset.addProperty("type", "integer");
        offset.addProperty("minimum", 0);
        offset.addProperty("maximum", Integer.MAX_VALUE);
        offset.addProperty("default", 0);
        properties.add("limit", limit);
        properties.add("offset", offset);
    }

    /**
     * Renders the common first line, with zero bounds for an empty page.
     * @param out the Markdown output
     * @param total the total number of matching entries
     * @param start the zero-based start, inclusive
     * @param end the zero-based end, exclusive
     */
    static void appendHeader(StringBuilder out, int total, int start, int end) {
        out.append(total).append(" total, showing ").append(start < end ? start + 1 : 0)
                .append('–').append(start < end ? end : 0).append('\n');
    }
}
