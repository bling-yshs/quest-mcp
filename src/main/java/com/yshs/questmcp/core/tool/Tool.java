package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;

/** An MCP tool: a named, described, read-only operation that answers with Markdown. */
public interface Tool {
    /** The tool name clients call, e.g. {@code get_status}. */
    String name();

    /** English description telling the model when and how to use the tool. */
    String description();

    /** JSON Schema of the {@code arguments} object. */
    JsonObject inputSchema();

    /**
     * Runs the tool.
     *
     * @param arguments the call's arguments (an empty object when the client sent none)
     * @return the answer as Markdown
     * @throws ToolException when the tool cannot answer; the message is shown to the model as an error result
     */
    String call(JsonObject arguments) throws ToolException;
}
