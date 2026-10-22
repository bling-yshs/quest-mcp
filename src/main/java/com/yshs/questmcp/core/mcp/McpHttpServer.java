package com.yshs.questmcp.core.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MCP Streamable HTTP endpoint on {@code http://127.0.0.1:<port>/mcp}: POST only, stateless, one JSON response per
 * request. Requests are served by a small pool of daemon threads, never by the game thread.
 *
 * <p>Only local MCP clients get through: requests from web pages of other origins are refused (403), as are requests
 * without the bearer token when one is configured (401).
 */
public final class McpHttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpHttpServer.class);
    private static final String HOST = "127.0.0.1";
    private static final String PATH = "/mcp";
    private static final int THREADS = 4;
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    /** Hosts of the web origins allowed to call the endpoint: pages served from this machine. */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final String BEARER = "Bearer ";

    private final HttpServer server;
    private final ExecutorService executor;
    private final McpDispatcher dispatcher;
    private final @Nullable byte[] token;

    /**
     * Retains the bound server and request-processing state for one endpoint.
     * @param server bound HTTP server
     * @param executor request worker pool
     * @param dispatcher MCP request dispatcher
     * @param token expected bearer token bytes, or null when authentication is disabled
     */
    private McpHttpServer(HttpServer server, ExecutorService executor, McpDispatcher dispatcher,
            @Nullable byte[] token) {
        this.server = server;
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.token = token;
    }

    /**
     * Binds {@code 127.0.0.1:port} and starts serving.
     *
     * @param port  the port, or {@code 0} for an ephemeral one
     * @param token the token clients must send as {@code Authorization: Bearer <token>}, or blank for none
     * @throws IOException if the port cannot be bound (e.g. it is already in use)
     */
    public static McpHttpServer start(int port, String token, McpDispatcher dispatcher) throws IOException {
        InetAddress loopback = InetAddress.getByAddress(HOST, new byte[] {127, 0, 0, 1});
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS, daemonThreads());
        byte[] tokenBytes = token.isBlank() ? null : token.strip().getBytes(StandardCharsets.UTF_8);
        McpHttpServer mcp = new McpHttpServer(server, executor, dispatcher, tokenBytes);
        // The root context sees every path, so that only exactly PATH is served (a context matches by prefix).
        server.createContext("/", mcp::handle);
        server.setExecutor(executor);
        server.start();
        return mcp;
    }

    /** The endpoint URL clients connect to. */
    public URI url() {
        return URI.create("http://" + HOST + ":" + server.getAddress().getPort() + PATH);
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) {
        try (exchange) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !isLocalOrigin(origin)) {
                // A web page from elsewhere, e.g. a malicious site that rebound its DNS name to 127.0.0.1.
                sendJson(exchange, 403, McpDispatcher.error(JsonNull.INSTANCE, JsonRpcException.INVALID_REQUEST,
                        "Forbidden: requests from web pages at " + origin + " are not allowed"));
                return;
            }
            if (!PATH.equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                sendJson(exchange, 401, McpDispatcher.error(JsonNull.INSTANCE, JsonRpcException.INVALID_REQUEST,
                        "Unauthorized: send the token from the Quest MCP config as \"Authorization: Bearer <token>\""));
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                // No SSE stream and no sessions, so GET and DELETE have nothing to offer.
                exchange.getResponseHeaders().set("Allow", "POST");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String protocolVersion = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
            if (protocolVersion != null && !ProtocolVersions.isSupported(protocolVersion)) {
                // Required by MCP 2025-06-18+. It also makes clients of newer, handshake-less revisions fall back to
                // initialize: they treat a 400 without a recognised modern error as a handshake-based server.
                sendJson(exchange, 400, McpDispatcher.error(JsonNull.INSTANCE, JsonRpcException.INVALID_REQUEST,
                        "Unsupported MCP-Protocol-Version " + protocolVersion + "; supported: "
                                + String.join(", ", ProtocolVersions.SUPPORTED)));
                return;
            }
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            McpDispatcher.Reply reply = dispatcher.handle(body);
            switch (reply.kind()) {
                case RESPONSE -> sendJson(exchange, 200, reply.body());
                case ACCEPTED -> exchange.sendResponseHeaders(202, -1);
                case REJECTED -> sendJson(exchange, 400, reply.body());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to handle MCP request", e);
        }
    }

    /** Whether an {@code Origin} header names a page served from this machine. */
    private static boolean isLocalOrigin(String origin) {
        try {
            String host = new URI(origin).getHost();
            // Opaque origins ("null", e.g. sandboxed frames or file: pages) have no host and are refused too.
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isAuthorized(@Nullable String authorization) {
        if (token == null) {
            return true;
        }
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return false;
        }
        byte[] presented = authorization.substring(BEARER.length()).strip().getBytes(StandardCharsets.UTF_8);
        // Constant-time, so that response timing does not reveal how much of a guessed token is right.
        return MessageDigest.isEqual(token, presented);
    }

    private static void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "Quest MCP HTTP #" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
