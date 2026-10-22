package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yshs.questmcp.core.tool.Tool;
import com.yshs.questmcp.core.tool.ToolException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tool arguments are checked against the tool's input schema, and tool failures reach the model as error results. */
class ToolArgumentsTest {
    /** Every rule of the supported JSON Schema subset. */
    private static final String SEARCH_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {"type": "string", "description": "What to look for"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 50, "default": 10},
                "weight": {"type": "number", "minimum": 0.5},
                "kind": {"type": "string", "enum": ["item", "fluid"]},
                "exact": {"type": "boolean"}
              },
              "required": ["query"],
              "additionalProperties": false
            }""";

    private final AtomicInteger searchCalls = new AtomicInteger();
    private McpTestServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = McpTestServer.withTools(List.of(
                new TestTool("search", SEARCH_SCHEMA, arguments -> {
                    searchCalls.incrementAndGet();
                    return "searched with " + arguments;
                }),
                new TestTool("lenient", "{\"type\": \"object\", \"properties\": {\"query\": {\"type\": \"string\"}}}",
                        arguments -> "called with " + arguments),
                new TestTool("not_found", "{\"type\": \"object\"}", arguments -> {
                    throw new ToolException("No quest with ID `0123ABCD` exists.");
                }),
                new TestTool("broken", "{\"type\": \"object\"}", arguments -> {
                    throw new IllegalStateException("adapter bug");
                })));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"query\": \"snowball\"}",
            "{\"query\": \"snowball\", \"limit\": 1, \"weight\": 0.5, \"kind\": \"item\", \"exact\": true}",
            "{\"query\": \"\", \"limit\": 50, \"weight\": 7, \"kind\": \"fluid\", \"exact\": false}",
            "{\"query\": \"snowball\", \"limit\": 10.0}"})
    void validArgumentsReachTheTool(String arguments) throws Exception {
        JsonObject result = callTool("search", arguments);

        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        assertTrue(McpTestServer.text(result).startsWith("searched with "), result.toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{}                                     | missing required argument \"query\"",
            "{\"query\": \"a\", \"page\": 2} | unknown argument \"page\" (expected \"query\", \"limit\", \"weight\",",
            "{\"query\": 5}                         | argument \"query\" must be a string, got 5",
            "{\"query\": null}                      | argument \"query\" must be a string, got null",
            "{\"query\": [\"a\"]}                   | argument \"query\" must be a string",
            "{\"query\": \"a\", \"limit\": \"10\"}  | argument \"limit\" must be an integer, got \"10\"",
            "{\"query\": \"a\", \"limit\": 2.5}     | argument \"limit\" must be an integer, got 2.5",
            "{\"query\": \"a\", \"limit\": 0}       | argument \"limit\" must be at least 1, got 0",
            "{\"query\": \"a\", \"limit\": 51}      | argument \"limit\" must be at most 50, got 51",
            "{\"query\": \"a\", \"limit\": 1e999999} | argument \"limit\"",
            "{\"query\": \"a\", \"weight\": 0.25}   | argument \"weight\" must be at least 0.5, got 0.25",
            "{\"query\": \"a\", \"weight\": \"1\"}  | argument \"weight\" must be a number",
            "{\"query\": \"a\", \"kind\": \"gas\"}  | argument \"kind\" must be one of [\"item\",\"fluid\"], got",
            "{\"query\": \"a\", \"exact\": \"yes\"} | argument \"exact\" must be true or false"})
    void argumentsViolatingTheSchemaAreAToolError(String arguments, String violation) throws Exception {
        JsonObject result = callTool("search", arguments);

        // A tool result rather than a JSON-RPC error, so that the model sees it and can fix its call.
        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        String message = McpTestServer.text(result);
        assertTrue(message.startsWith("Invalid arguments for tool search: "), message);
        assertTrue(message.contains(violation), message);
        assertEquals(0, searchCalls.get(), "the tool must not run");
    }

    @Test
    void everyViolationIsReportedAtOnce() throws Exception {
        String message = McpTestServer.text(callTool("search", "{\"limit\": 0, \"page\": 2}"));

        assertTrue(message.contains("missing required argument \"query\""), message);
        assertTrue(message.contains("\"limit\" must be at least 1"), message);
        assertTrue(message.contains("unknown argument \"page\""), message);
    }

    @Test
    void undeclaredArgumentsAreAllowedUnlessTheSchemaForbidsThem() throws Exception {
        JsonObject result = callTool("lenient", "{\"query\": \"a\", \"extra\": [1, 2]}");

        assertFalse(result.get("isError").getAsBoolean(), result.toString());
    }

    @Test
    void aToolThatCannotAnswerReturnsAnErrorResult() throws Exception {
        JsonObject result = server.callTool("not_found", new JsonObject());

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertEquals("No quest with ID `0123ABCD` exists.", McpTestServer.text(result));
    }

    @Test
    void anUnexpectedToolFailureReturnsAnErrorResult() throws Exception {
        JsonObject result = server.callTool("broken", new JsonObject());

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        String text = McpTestServer.text(result);
        assertTrue(text.contains("Internal error in broken"), text);
        assertTrue(text.contains("adapter bug"), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\": \"array\"}",
            "{\"type\": \"object\", \"properties\": {\"ids\": {\"type\": \"array\"}}}",
            "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"string\", \"pattern\": \"^[0-9A-F]+$\"}}}",
            "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": [\"string\", \"null\"]}}}",
            "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"string\", \"minimum\": 1}}}",
            "{\"type\": \"object\", \"required\": [\"id\"]}",
            "{\"type\": \"object\", \"anyOf\": []}"})
    void aServerWithAToolSchemaOutsideTheSupportedSubsetDoesNotStart(String schema) {
        assertThrows(IllegalArgumentException.class,
                () -> McpTestServer.withTools(List.of(new TestTool("odd", schema, arguments -> ""))));
    }

    /** Calls a tool with arguments given as JSON text and returns the tool result. */
    private JsonObject callTool(String name, String arguments) throws Exception {
        return server.callTool(name, JsonParser.parseString(arguments).getAsJsonObject());
    }

    @FunctionalInterface
    private interface Handler {
        String call(JsonObject arguments) throws ToolException;
    }

    private record TestTool(String name, String schema, Handler handler) implements Tool {
        @Override
        public String description() {
            return "A tool for tests.";
        }

        @Override
        public JsonObject inputSchema() {
            return JsonParser.parseString(schema).getAsJsonObject();
        }

        @Override
        public String call(JsonObject arguments) throws ToolException {
            return handler.call(arguments);
        }
    }
}
