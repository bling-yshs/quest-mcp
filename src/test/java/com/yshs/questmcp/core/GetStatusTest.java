package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GetStatusTest {
    private static final String GAME_THREAD = "fake game thread";

    private final FakeGameSource game = new FakeGameSource();
    private final ExecutorService gameThread = Executors.newSingleThreadExecutor(task -> new Thread(task, GAME_THREAD));
    private McpTestServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
        gameThread.shutdownNow();
    }

    static Stream<Arguments> gameStates() {
        return Stream.of(
                Arguments.of(new GameStatus(false, false, false, "en_us"), "NOT_IN_WORLD",
                        "not in a world", "not loaded", "not ready"),
                Arguments.of(new GameStatus(true, false, true, "en_us"), "QUESTS_NOT_LOADED",
                        "quest book has not been received", "not loaded", "ready"),
                Arguments.of(new GameStatus(true, true, false, "en_us"), "JEI_NOT_READY",
                        "JEI has not finished loading", "loaded", "not ready"),
                Arguments.of(new GameStatus(true, true, true, "en_us"), "READY",
                        "quests and recipes are available", "loaded", "ready"));
    }

    @ParameterizedTest
    @MethodSource("gameStates")
    void reportsTheGameStateWithAnExplanation(GameStatus status, String state, String explanation, String quests,
            String jei) throws Exception {
        startServer(Duration.ofSeconds(5));
        game.set(status);

        JsonObject result = server.callTool("get_status", new JsonObject());

        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        String text = McpTestServer.text(result);
        assertTrue(text.contains("Game state: `" + state + "`"), text);
        assertTrue(text.contains(explanation), text);
        assertTrue(text.contains("Quest book: " + quests + "\n"), text);
        assertTrue(text.contains("JEI: " + jei + "\n"), text);
    }

    @Test
    void reportsModVersionLanguageAndRevealHiddenQuests() throws Exception {
        startServer(Duration.ofSeconds(5));
        game.set(new GameStatus(true, true, true, "zh_cn"));

        String text = McpTestServer.text(server.callTool("get_status", new JsonObject()));

        assertTrue(text.contains("Mod version: `" + McpTestServer.MOD_VERSION + "`"), text);
        assertTrue(text.contains("Language: `zh_cn`"), text);
        assertTrue(text.contains("revealHiddenQuests: `false`"), text);
    }

    @Test
    void readsTheGameOnTheGameThread() throws Exception {
        startServer(Duration.ofSeconds(5));

        server.callTool("get_status", new JsonObject());

        assertEquals(GAME_THREAD, game.lastReadThread());
    }

    @Test
    void answersWithAnErrorWhenTheGameDoesNotRespondInTime() throws Exception {
        // A game thread that never gets round to running anything.
        server = new McpTestServer(game, task -> { }, Duration.ofMillis(200));

        JsonObject result = server.callTool("get_status", new JsonObject());

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertTrue(McpTestServer.text(result).contains("did not respond within 200 ms"), result.toString());
    }

    private void startServer(Duration gameTimeout) throws Exception {
        server = new McpTestServer(game, gameThread, gameTimeout);
    }
}
