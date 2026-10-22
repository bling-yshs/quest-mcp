package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ServerStartTest {
    /**
     * Checks binding failures remain readable with both data sources connected.
     * @throws Exception if socket setup fails
     */
    @Test
    void aPortInUseByAnotherProgramFailsWithAReadableReason() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        try (ServerSocket otherProgram = new ServerSocket(0, 50, loopback)) {
            int port = otherProgram.getLocalPort();

            IOException failure = assertThrows(IOException.class, () -> QuestMcpServer.start(port, "",
                    McpTestServer.MOD_VERSION, new FakeGameSource(), new FakeQuestSource(), new FakeRecipeSource(), () -> false, Runnable::run,
                    Duration.ofSeconds(5)));
            ServerStatus.Failed status = ServerStatus.Failed.of(port, failure);

            assertEquals(port, status.port());
            // The OS wording, e.g. "Address already in use" (Linux) or "Address already in use: bind" (Windows).
            assertTrue(status.reason().startsWith("port " + port + " is unavailable: "), status.reason());
            assertTrue(status.reason().contains("already in use"), status.reason());
            // The OS wording alone, for the translated in-game message.
            assertTrue(status.detail().contains("already in use"), status.detail());
        }
    }

    /**
     * Verifies a failed bind can recover on exactly the same port after the other instance releases it.
     * @throws Exception if socket setup or an HTTP request fails
     */
    @Test
    void retriesTheSamePortAfterTheOtherInstanceStops() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        int port;
        try (ServerSocket otherInstance = new ServerSocket(0, 50, loopback)) {
            port = otherInstance.getLocalPort();
            assertThrows(IOException.class, () -> QuestMcpServer.start(port, "", McpTestServer.MOD_VERSION,
                    new FakeGameSource(), new FakeQuestSource(), new FakeRecipeSource(), () -> false,
                    Runnable::run, Duration.ofSeconds(5)));
        }
        QuestMcpServer recovered = QuestMcpServer.start(port, "", McpTestServer.MOD_VERSION,
                new FakeGameSource(), new FakeQuestSource(), new FakeRecipeSource(), () -> false,
                Runnable::run, Duration.ofSeconds(5));
        try (HttpClient http = HttpClient.newHttpClient()) {
            assertEquals(URI.create("http://127.0.0.1:" + port + "/mcp"), recovered.url());
            HttpRequest ping = HttpRequest.newBuilder(recovered.url()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                    .build();
            HttpResponse<String> response = http.send(ping, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"result\""), response.body());
        } finally {
            recovered.stop();
        }
    }

    /**
     * Verifies stopping and starting an endpoint preserves its URL and bearer-token behavior.
     * @throws Exception if socket setup or an HTTP request fails
     */
    @Test
    void restartsOnTheSamePortWithTheSameAuthentication() throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 50, loopback)) {
            port = reservation.getLocalPort();
        }
        URI url = URI.create("http://127.0.0.1:" + port + "/mcp");
        try (HttpClient http = HttpClient.newHttpClient()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                QuestMcpServer server = QuestMcpServer.start(port, "test-token", McpTestServer.MOD_VERSION,
                        new FakeGameSource(), new FakeQuestSource(), new FakeRecipeSource(), () -> false,
                        Runnable::run, Duration.ofSeconds(5));
                try {
                    assertEquals(url, server.url());
                    HttpResponse<String> unauthorized = http.send(HttpRequest.newBuilder(url).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(401, unauthorized.statusCode());
                    HttpRequest ping = HttpRequest.newBuilder(url).header("Content-Type", "application/json")
                            .header("Authorization", "Bearer test-token")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                            .build();
                    HttpResponse<String> response = http.send(ping, HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode(), response.body());
                    assertTrue(response.body().contains("\"result\""), response.body());
                } finally {
                    server.stop();
                }
            }
        }
    }
}
