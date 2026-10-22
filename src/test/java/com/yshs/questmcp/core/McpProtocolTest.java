package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class McpProtocolTest {
    private McpTestServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new McpTestServer(new FakeGameSource(), Runnable::run, Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-03-26", "2025-06-18", "2025-11-25"})
    void initializeEchoesASupportedProtocolVersion(String version) throws Exception {
        JsonObject result = server.request("initialize", initializeParams(version)).getAsJsonObject("result");

        assertEquals(version, result.get("protocolVersion").getAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-11-05", "2099-01-01"})
    void initializeAnswersTheNewestVersionWhenTheClientsIsUnsupported(String version) throws Exception {
        JsonObject result = server.request("initialize", initializeParams(version)).getAsJsonObject("result");

        assertEquals("2025-11-25", result.get("protocolVersion").getAsString());
    }

    @Test
    void initializeAdvertisesOnlyToolsAndIdentifiesTheServer() throws Exception {
        JsonObject response = server.request("initialize", initializeParams("2025-11-25"));

        assertEquals("2.0", response.get("jsonrpc").getAsString());
        assertEquals(1, response.get("id").getAsInt());
        JsonObject result = response.getAsJsonObject("result");
        assertEquals(Set.of("tools"), result.getAsJsonObject("capabilities").keySet());
        JsonObject serverInfo = result.getAsJsonObject("serverInfo");
        assertEquals("questmcp", serverInfo.get("name").getAsString());
        assertEquals(McpTestServer.MOD_VERSION, serverInfo.get("version").getAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"notifications/initialized", "notifications/cancelled", "notifications/unknown"})
    void notificationsAreAcceptedWithoutABody(String method) throws Exception {
        HttpResponse<String> response = server.post("{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\"}");

        assertEquals(202, response.statusCode());
        assertEquals("", response.body());
    }

    @Test
    void pingReturnsAnEmptyResultForTheSameId() throws Exception {
        HttpResponse<String> response = server.post("{\"jsonrpc\":\"2.0\",\"id\":\"ping-1\",\"method\":\"ping\"}");

        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("ping-1", body.get("id").getAsString());
        assertEquals(new JsonObject(), body.get("result"));
    }

    @Test
    void toolsListDescribesGetStatus() throws Exception {
        JsonObject result = server.request("tools/list", new JsonObject()).getAsJsonObject("result");

        JsonObject getStatus = null;
        for (JsonElement tool : result.getAsJsonArray("tools")) {
            if ("get_status".equals(tool.getAsJsonObject().get("name").getAsString())) {
                getStatus = tool.getAsJsonObject();
            }
        }
        assertTrue(getStatus != null, "get_status is not listed: " + result);
        assertTrue(getStatus.get("description").getAsString().contains("in a world"), getStatus.toString());
        JsonObject schema = getStatus.getAsJsonObject("inputSchema");
        assertEquals("object", schema.get("type").getAsString());
        assertEquals(new JsonObject(), schema.get("properties"));
    }

    @Test
    void everyListedToolHasANameDescriptionAndObjectSchema() throws Exception {
        JsonObject result = server.request("tools/list", new JsonObject()).getAsJsonObject("result");

        assertFalse(result.getAsJsonArray("tools").isEmpty());
        for (JsonElement element : result.getAsJsonArray("tools")) {
            JsonObject tool = element.getAsJsonObject();
            assertFalse(tool.get("name").getAsString().isBlank(), tool.toString());
            assertFalse(tool.get("description").getAsString().isBlank(), tool.toString());
            assertEquals("object", tool.getAsJsonObject("inputSchema").get("type").getAsString(), tool.toString());
        }
    }

    @Test
    void requestsDeclaringAnUnsupportedProtocolVersionAreRejected() throws Exception {
        HttpResponse<String> response = server.post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                "MCP-Protocol-Version", "2026-07-28");

        assertEquals(400, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(body.getAsJsonObject("error").get("message").getAsString().contains("2025-11-25"), body.toString());
    }

    @Test
    void requestsDeclaringASupportedProtocolVersionAreServed() throws Exception {
        HttpResponse<String> response = server.post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                "MCP-Protocol-Version", "2025-06-18");

        assertEquals(200, response.statusCode());
        assertTrue(JsonParser.parseString(response.body()).getAsJsonObject().has("result"), response.body());
    }

    @Test
    void malformedJsonIsAParseError() throws Exception {
        HttpResponse<String> response = server.post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":");

        assertEquals(400, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(-32700, errorCode(body), body.toString());
        assertTrue(body.get("id").isJsonNull(), body.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]",
            "\"ping\"",
            "{\"id\":1,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":42}",
            "{\"jsonrpc\":\"2.0\",\"id\":{\"n\":1},\"method\":\"ping\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":[1]}"})
    void invalidJsonRpcIsAnInvalidRequest(String message) throws Exception {
        HttpResponse<String> response = server.post(message);

        assertEquals(400, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertEquals(-32600, errorCode(body), body.toString());
    }

    @Test
    void anInvalidRequestWithAReadableIdIsAnsweredWithThatId() throws Exception {
        HttpResponse<String> response = server.post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":42}");

        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(7, body.get("id").getAsInt(), body.toString());
    }

    @Test
    void unknownMethodIsMethodNotFound() throws Exception {
        JsonObject response = server.request("resources/list", new JsonObject());

        assertEquals(-32601, errorCode(response), response.toString());
        assertEquals(1, response.get("id").getAsInt());
        assertTrue(response.getAsJsonObject("error").get("message").getAsString().contains("resources/list"),
                response.toString());
    }

    @Test
    void callingAnUnknownToolIsInvalidParams() throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("name", "complete_quest");

        JsonObject response = server.request("tools/call", params);

        assertEquals(-32602, errorCode(response), response.toString());
        assertTrue(errorMessage(response).contains("Unknown tool: complete_quest"), response.toString());
    }

    @Test
    void callingAToolWithoutANameIsInvalidParams() throws Exception {
        JsonObject response = server.request("tools/call", new JsonObject());

        assertEquals(-32602, errorCode(response), response.toString());
    }

    @Test
    void argumentsThatAreNotAnObjectAreInvalidParams() throws Exception {
        JsonObject params = new JsonObject();
        params.addProperty("name", "get_status");
        params.addProperty("arguments", "verbose");

        JsonObject response = server.request("tools/call", params);

        assertEquals(-32602, errorCode(response), response.toString());
    }

    @Test
    void argumentsGetStatusDoesNotDeclareAreAToolError() throws Exception {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("verbose", true);

        JsonObject result = server.callTool("get_status", arguments);

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertTrue(McpTestServer.text(result).contains("unknown argument \"verbose\" (this tool takes no arguments)"),
                result.toString());
    }

    private static int errorCode(JsonObject response) {
        assertFalse(response.has("result"), response.toString());
        return response.getAsJsonObject("error").get("code").getAsInt();
    }

    private static String errorMessage(JsonObject response) {
        return response.getAsJsonObject("error").get("message").getAsString();
    }

    private static JsonObject initializeParams(String protocolVersion) {
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "test-client");
        clientInfo.addProperty("version", "0.0.1");
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", protocolVersion);
        params.add("capabilities", new JsonObject());
        params.add("clientInfo", clientInfo);
        return params;
    }
}
