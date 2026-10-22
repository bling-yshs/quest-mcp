package com.yshs.questmcp.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yshs.questmcp.core.mcp.McpDispatcher;
import com.yshs.questmcp.core.mcp.McpHttpServer;
import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.tool.Tool;
import com.yshs.questmcp.core.tool.ToolRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** The real Quest MCP server on an ephemeral port, wired to fakes, plus a minimal MCP client for driving it. */
final class McpTestServer implements AutoCloseable {
    static final String MOD_VERSION = "1.2.3-test";

    private final URI url;
    private final Runnable stop;
    private final HttpClient http = HttpClient.newHttpClient();
    private int nextId = 1;

    McpTestServer(GameSource game, Executor gameExecutor, Duration gameTimeout) throws IOException {
        this(game, gameExecutor, gameTimeout, "");
    }

    /**
     * Starts an endpoint with empty quest and ingredient data.
     * @param game game readiness
     * @param gameExecutor game-thread executor
     * @param gameTimeout read timeout
     * @param token optional bearer token
     * @throws IOException if the endpoint cannot start
     */
    McpTestServer(GameSource game, Executor gameExecutor, Duration gameTimeout, String token) throws IOException {
        this(QuestMcpServer.start(0, token, MOD_VERSION, game, new FakeQuestSource(), new FakeRecipeSource(), () -> false, gameExecutor,
                gameTimeout));
    }

    /**
     * Starts an endpoint with the supplied quest book.
     * @param game game readiness
     * @param quests quest source
     * @param revealHiddenQuests visibility configuration
     * @param gameExecutor game-thread executor
     * @param gameTimeout read timeout
     * @throws IOException if the endpoint cannot start
     */
    McpTestServer(GameSource game, QuestSource quests, BooleanSupplier revealHiddenQuests, Executor gameExecutor,
            Duration gameTimeout) throws IOException {
        this(QuestMcpServer.start(0, "", MOD_VERSION, game, quests, new FakeRecipeSource(), revealHiddenQuests, gameExecutor, gameTimeout));
    }

    /**
     * Starts a server reading the supplied recipe source.
     * @param game game readiness
     * @param recipes fake JEI source
     * @throws IOException if the server cannot start
     */
    McpTestServer(GameSource game, com.yshs.questmcp.core.source.RecipeSource recipes) throws IOException {
        this(QuestMcpServer.start(0, "", MOD_VERSION, game, new FakeQuestSource(), recipes, () -> false,
                Runnable::run, Duration.ofSeconds(5)));
    }

    private McpTestServer(QuestMcpServer server) {
        this(server.url(), server::stop);
    }

    private McpTestServer(URI url, Runnable stop) {
        this.url = url;
        this.stop = stop;
    }

    /** The real endpoint and dispatcher serving test-specific tools instead of Quest MCP's own. */
    static McpTestServer withTools(List<Tool> tools) throws IOException {
        McpHttpServer server = McpHttpServer.start(0, "",
                new McpDispatcher(QuestMcpServer.NAME, MOD_VERSION, new ToolRegistry(tools)));
        return new McpTestServer(server.url(), server::stop);
    }

    URI url() {
        return url;
    }

    /** A URL on the server with the given path (and query) instead of the endpoint's. */
    URI url(String pathAndQuery) {
        return url.resolve(pathAndQuery);
    }

    /** POSTs a raw body with the headers a Streamable HTTP client sends, plus {@code extraHeaders} (name, value...). */
    HttpResponse<String> post(String body, String... extraHeaders) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        for (int i = 0; i < extraHeaders.length; i += 2) {
            request.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Sends a JSON-RPC request and returns the parsed JSON-RPC response, asserting HTTP 200 with a JSON body. */
    JsonObject request(String method, JsonObject params) throws IOException, InterruptedException {
        JsonObject message = new JsonObject();
        message.addProperty("jsonrpc", "2.0");
        message.addProperty("id", nextId++);
        message.addProperty("method", method);
        message.add("params", params);
        HttpResponse<String> response = post(message.toString());
        if (response.statusCode() != 200) {
            throw new AssertionError("Expected HTTP 200 but got " + response.statusCode() + ": " + response.body());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.startsWith("application/json")) {
            throw new AssertionError("Expected a JSON response but got Content-Type " + contentType);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Calls a tool and returns the JSON-RPC {@code result} (content + isError). */
    JsonObject callTool(String name, JsonObject arguments) throws IOException, InterruptedException {
        JsonObject params = new JsonObject();
        params.addProperty("name", name);
        params.add("arguments", arguments);
        JsonObject response = request("tools/call", params);
        JsonElement result = response.get("result");
        if (result == null) {
            throw new AssertionError("Expected a result but got " + response);
        }
        return result.getAsJsonObject();
    }

    /** The single text block of a tool result. */
    static String text(JsonObject toolResult) {
        var content = toolResult.getAsJsonArray("content");
        if (content.size() != 1 || !"text".equals(content.get(0).getAsJsonObject().get("type").getAsString())) {
            throw new AssertionError("Expected a single text block but got " + content);
        }
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    @Override
    public void close() {
        stop.run();
    }
}
