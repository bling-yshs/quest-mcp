package com.yshs.questmcp.core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.yshs.questmcp.core.tool.Tool;
import com.yshs.questmcp.core.tool.ToolException;
import com.yshs.questmcp.core.tool.ToolRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles one JSON-RPC message of the MCP protocol: {@code initialize}, {@code ping}, {@code tools/list},
 * {@code tools/call} and notifications. Stateless: every message is handled on its own.
 */
public final class McpDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpDispatcher.class);

    private final String serverName;
    private final String serverVersion;
    private final ToolRegistry tools;
    private final Map<String, ArgumentValidator> validators = new HashMap<>();

    /** @throws IllegalArgumentException if a tool's input schema uses more than {@link ArgumentValidator} supports */
    public McpDispatcher(String serverName, String serverVersion, ToolRegistry tools) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.tools = tools;
        for (Tool tool : tools.all()) {
            try {
                validators.put(tool.name(), new ArgumentValidator(tool.inputSchema()));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid input schema of tool " + tool.name() + ": " + e.getMessage(), e);
            }
        }
    }

    /** What to send back for one incoming message. */
    public record Reply(Kind kind, @Nullable JsonObject body) {
        public enum Kind {
            /** A JSON-RPC response to a request (which may carry a JSON-RPC error). */
            RESPONSE,
            /** A notification (or a client response) was accepted; nothing to send back. */
            ACCEPTED,
            /** The message is not valid JSON-RPC; the body is a JSON-RPC error. */
            REJECTED
        }
    }

    public Reply handle(String message) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(message);
        } catch (JsonParseException e) {
            return rejected(JsonNull.INSTANCE, JsonRpcException.PARSE_ERROR, "Parse error: the body is not valid JSON");
        }
        if (parsed.isJsonArray()) {
            return rejected(JsonNull.INSTANCE, JsonRpcException.INVALID_REQUEST, "Batch requests are not supported");
        }
        if (!parsed.isJsonObject()) {
            return rejected(JsonNull.INSTANCE, JsonRpcException.INVALID_REQUEST, "The body must be a JSON-RPC object");
        }
        return handle(parsed.getAsJsonObject());
    }

    /**
     * Validates and dispatches a JSON-RPC message, logging the elapsed time of each dispatched request.
     *
     * @param message the parsed JSON-RPC message
     * @return the response or notification acknowledgement
     */
    private Reply handle(JsonObject message) {
        JsonElement id = message.get("id");
        boolean isNotification = id == null;
        if (!isNotification && !isValidId(id)) {
            return rejected(JsonNull.INSTANCE, JsonRpcException.INVALID_REQUEST, "\"id\" must be a string or a number");
        }
        JsonElement responseId = isNotification ? JsonNull.INSTANCE : id;
        if (!isString(message.get("jsonrpc"), "2.0")) {
            return rejected(responseId, JsonRpcException.INVALID_REQUEST, "\"jsonrpc\" must be \"2.0\"");
        }
        JsonElement method = message.get("method");
        if (method == null && !isNotification && (message.has("result") || message.has("error"))) {
            // A response to a server-initiated request. This server sends none, so there is nothing to do.
            return new Reply(Reply.Kind.ACCEPTED, null);
        }
        if (!isString(method)) {
            return rejected(responseId, JsonRpcException.INVALID_REQUEST, "\"method\" must be a string");
        }
        JsonElement params = message.get("params");
        if (params != null && !params.isJsonNull() && !params.isJsonObject()) {
            return rejected(responseId, JsonRpcException.INVALID_REQUEST, "\"params\" must be an object");
        }
        if (isNotification) {
            // notifications/initialized, notifications/cancelled, ...: nothing to do for a stateless server.
            return new Reply(Reply.Kind.ACCEPTED, null);
        }

        JsonObject paramsObject = params instanceof JsonObject object ? object : new JsonObject();
        String methodName = method.getAsString();
        JsonElement toolName = paramsObject.get("name");
        String loggedToolName = "tools/call".equals(methodName) && isString(toolName) ? toolName.getAsString() : "-";
        long startedAt = System.nanoTime();
        try {
            JsonObject result = switch (methodName) {
                case "initialize" -> initialize(paramsObject);
                case "ping" -> new JsonObject();
                case "tools/list" -> listTools();
                case "tools/call" -> callTool(paramsObject);
                default -> throw new JsonRpcException(JsonRpcException.METHOD_NOT_FOUND,
                        "Method not found: " + method.getAsString());
            };
            JsonObject response = envelope(id);
            response.add("result", result);
            return new Reply(Reply.Kind.RESPONSE, response);
        } catch (JsonRpcException e) {
            return new Reply(Reply.Kind.RESPONSE, error(id, e.code(), e.getMessage()));
        } finally {
            double durationMs = (System.nanoTime() - startedAt) / 1_000_000.0;
            LOGGER.info("MCP call completed: method={}, tool={}, id={}, durationMs={}",
                    methodName, loggedToolName, id, durationMs);
        }
    }

    private JsonObject initialize(JsonObject params) {
        JsonElement requested = params.get("protocolVersion");
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", ProtocolVersions.negotiate(isString(requested) ? requested.getAsString() : null));
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", serverName);
        serverInfo.addProperty("version", serverVersion);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject listTools() {
        JsonArray list = new JsonArray();
        for (Tool tool : tools.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            list.add(entry);
        }
        JsonObject result = new JsonObject();
        result.add("tools", list);
        return result;
    }

    private JsonObject callTool(JsonObject params) throws JsonRpcException {
        JsonElement name = params.get("name");
        if (!isString(name)) {
            throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "\"name\" must be the name of a tool");
        }
        Tool tool = tools.find(name.getAsString()).orElseThrow(() -> new JsonRpcException(
                JsonRpcException.INVALID_PARAMS, "Unknown tool: " + name.getAsString()));
        JsonElement arguments = params.get("arguments");
        if (arguments != null && !arguments.isJsonNull() && !arguments.isJsonObject()) {
            throw new JsonRpcException(JsonRpcException.INVALID_PARAMS, "\"arguments\" must be an object");
        }
        JsonObject argumentsObject = arguments instanceof JsonObject object ? object : new JsonObject();
        List<String> violations = validators.get(tool.name()).violations(argumentsObject);
        if (!violations.isEmpty()) {
            // A tool error rather than a JSON-RPC error, so that the model sees the problem and can fix its call
            // (MCP 2025-11-25, SEP-1303).
            return toolResult("Invalid arguments for tool " + tool.name() + ": " + String.join("; ", violations), true);
        }
        try {
            return toolResult(tool.call(argumentsObject), false);
        } catch (ToolException e) {
            return toolResult(e.getMessage(), true);
        } catch (RuntimeException e) {
            LOGGER.error("Tool {} failed", tool.name(), e);
            return toolResult("Internal error in " + tool.name() + ": " + e, true);
        }
    }

    private static JsonObject toolResult(String markdown, boolean isError) {
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", markdown);
        JsonArray content = new JsonArray();
        content.add(text);
        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    private static Reply rejected(JsonElement id, int code, String message) {
        return new Reply(Reply.Kind.REJECTED, error(id, code, message));
    }

    static JsonObject error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject response = envelope(id);
        response.add("error", error);
        return response;
    }

    private static JsonObject envelope(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        return response;
    }

    private static boolean isValidId(JsonElement id) {
        return id instanceof JsonPrimitive primitive && (primitive.isString() || primitive.isNumber());
    }

    private static boolean isString(@Nullable JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isString();
    }

    private static boolean isString(@Nullable JsonElement element, String value) {
        return isString(element) && element.getAsString().equals(value);
    }
}
