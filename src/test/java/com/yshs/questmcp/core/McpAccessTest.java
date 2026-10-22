package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Who may use the endpoint, and on which path and method. */
class McpAccessTest {
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
    private static final String TOKEN = "s3cret-Token";

    private McpTestServer server;

    @AfterEach
    void stopServer() {
        server.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.example",
            "https://evil.example:25599",
            "http://localhost.evil.example",
            "http://127.0.0.1.evil.example",
            "http://192.168.1.20:25599",
            "null",
            "not an origin"})
    void requestsFromForeignWebPagesAreForbidden(String origin) throws Exception {
        startServer("");

        HttpResponse<String> response = server.post(PING, "Origin", origin);

        assertEquals(403, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(body.getAsJsonObject("error").get("message").getAsString().contains("Forbidden"), body.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:6274",
            "https://LOCALHOST:8443",
            "http://127.0.0.1:25599",
            "http://[::1]:3000"})
    void requestsFromLocalWebPagesAreServed(String origin) throws Exception {
        startServer("");

        HttpResponse<String> response = server.post(PING, "Origin", origin);

        assertEquals(200, response.statusCode(), response.body());
    }

    @Test
    void requestsWithoutAnOriginAreServed() throws Exception {
        startServer("");

        assertEquals(200, server.post(PING).statusCode());
    }

    @Test
    void aForeignOriginIsForbiddenEvenWithTheRightToken() throws Exception {
        startServer(TOKEN);

        HttpResponse<String> response = server.post(PING, "Origin", "http://evil.example",
                "Authorization", "Bearer " + TOKEN);

        assertEquals(403, response.statusCode());
    }

    @Test
    void withoutAConfiguredTokenAnyAuthorizationIsIgnored() throws Exception {
        startServer("");

        assertEquals(200, server.post(PING, "Authorization", "Bearer anything").statusCode());
    }

    @Test
    void withATokenRequestsWithoutAuthorizationAreUnauthorized() throws Exception {
        startServer(TOKEN);

        HttpResponse<String> response = server.post(PING);

        assertEquals(401, response.statusCode());
        assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(""));
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(body.getAsJsonObject("error").get("message").getAsString().contains("Authorization: Bearer"),
                body.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bearer wrong",
            "Bearer s3cret-token",
            "Bearer s3cret-Token-and-more",
            "Bearer s3cret",
            "Bearer ",
            "Basic czNjcmV0LVRva2Vu",
            "s3cret-Token"})
    void withATokenRequestsWithTheWrongAuthorizationAreUnauthorized(String authorization) throws Exception {
        startServer(TOKEN);

        HttpResponse<String> response = server.post(PING, "Authorization", authorization);

        assertEquals(401, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer " + TOKEN, "bearer " + TOKEN, "Bearer  " + TOKEN + " "})
    void withATokenRequestsWithTheRightBearerTokenAreServed(String authorization) throws Exception {
        startServer(TOKEN);

        HttpResponse<String> response = server.post(PING, "Authorization", authorization);

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(JsonParser.parseString(response.body()).getAsJsonObject().has("result"), response.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/mcp/", "/mcpx", "/mcp/tools", "/MCP", "/.well-known/oauth-protected-resource"})
    void otherPathsAreNotFound(String path) throws Exception {
        startServer("");

        HttpResponse<String> response = server.send(HttpRequest.newBuilder(server.url(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(PING)));

        assertEquals(404, response.statusCode());
    }

    @Test
    void otherPathsAreNotFoundRatherThanUnauthorized() throws Exception {
        // MCP clients probe /.well-known/ for OAuth after a 401; a plain 404 there ends that search quickly.
        startServer(TOKEN);

        HttpResponse<String> response = server.send(HttpRequest.newBuilder(
                server.url("/.well-known/oauth-protected-resource/mcp")).GET());

        assertEquals(404, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "DELETE"})
    void onlyPostIsAllowed(String method) throws Exception {
        startServer("");

        HttpResponse<String> response = server.send(HttpRequest.newBuilder(server.url())
                .header("Accept", "text/event-stream")
                .method(method, HttpRequest.BodyPublishers.noBody()));

        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElse(""));
    }

    private void startServer(String token) throws Exception {
        server = new McpTestServer(new FakeGameSource(), Runnable::run, Duration.ofSeconds(5), token);
    }
}
