package com.yshs.questmcp.core.tool;

/**
 * A tool could not answer, for a reason the model should see and can react to (not in a world, unknown ID, timeout…).
 * Returned to the client as a tool result with {@code isError: true}, not as a JSON-RPC error.
 */
public class ToolException extends Exception {
    public ToolException(String message) {
        super(message);
    }
}
