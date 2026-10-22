package com.yshs.questmcp.core;

import static com.yshs.questmcp.core.FakeQuestSource.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestStatus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Endpoint checks for actionable quests and the one-step rule. */
class CurrentQuestsTest {
    /**
     * Checks priority, completed prerequisites, chained locks, unknown dependencies and hidden content.
     * @throws Exception if the endpoint fails
     */
    @Test
    void prioritizesActionableQuestsAndOnlyIncludesOneStepLocks() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, false, "en_us"));
        FakeQuestSource source = new FakeQuestSource();
        source.set(new QuestBook(List.of(chapter("c", "Start", true,
                quest("available", "c", "Available").build(),
                quest("done", "c", "Done").status(QuestStatus.COMPLETED).build(),
                quest("progress", "c", "Progress").status(QuestStatus.IN_PROGRESS).build(),
                quest("next", "c", "Next").status(QuestStatus.LOCKED)
                        .dependencies("done", "available", "progress").build(),
                quest("later", "c", "Later").status(QuestStatus.LOCKED).dependencies("next").build(),
                quest("unknown", "c", "Unknown").status(QuestStatus.LOCKED).dependencies("missing").build(),
                quest("empty", "c", "Empty").status(QuestStatus.LOCKED).build(),
                quest("hidden", "c", "Secret").hidden().build(),
                quest("secret-next", "c", "Secret next").status(QuestStatus.LOCKED)
                        .dependencies("hidden").build()),
                chapter("secret", "Secret chapter", false,
                        quest("vault", "secret", "Vault").build()))));
        AtomicBoolean reveal = new AtomicBoolean();
        try (McpTestServer server = new McpTestServer(game, source, reveal::get, Runnable::run, Duration.ofSeconds(5))) {
            String result = text(server, new JsonObject());
            assertTrue(result.startsWith("3 total, showing 1–3\n"), result);
            assertTrue(result.indexOf("`progress`") < result.indexOf("`available`"), result);
            assertTrue(result.indexOf("`available`") < result.indexOf("`next`"), result);
            assertTrue(result.contains("One step away; blocked by: `available` Available (AVAILABLE), "
                    + "`progress` Progress (IN_PROGRESS)"), result);
            for (String excluded : List.of("done", "later", "unknown", "empty", "hidden", "secret-next", "vault")) {
                assertFalse(result.contains("`" + excluded + "`"), result);
            }
            reveal.set(true);
            String revealed = text(server, new JsonObject());
            assertTrue(revealed.startsWith("6 total"), revealed);
            assertTrue(revealed.contains("Secret (AVAILABLE, hidden)"), revealed);
            assertTrue(revealed.contains("Vault (AVAILABLE, hidden)"), revealed);
            assertTrue(revealed.contains("`secret-next`"), revealed);
        }
    }

    /**
     * Checks paging bounds and game-language rendering through the endpoint.
     * @throws Exception if the endpoint fails
     */
    @Test
    void pagesAndLocalizesAndReportsUnavailableState() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "zh_cn"));
        FakeQuestSource source = new FakeQuestSource();
        source.set(new QuestBook(List.of(chapter("c", "Start", true,
                quest("q1", "c", "一").build(),
                quest("q2", "c", "Two").build()))));
        try (McpTestServer server = new McpTestServer(game, source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            assertTrue(text(server, new JsonObject()).contains("一 (AVAILABLE)"));
            JsonObject args = new JsonObject();
            args.addProperty("limit", 1);
            args.addProperty("offset", 1);
            String page = text(server, args);
            assertTrue(page.startsWith("2 total, showing 2–2\n"), page);
            assertTrue(page.contains("Two"), page);
            assertFalse(page.contains("一"), page);
            args.addProperty("offset", Integer.MAX_VALUE);
            assertTrue(text(server, args).startsWith("2 total, showing 0–0"));
            args.addProperty("limit", 51);
            assertTrue(server.callTool("get_current_quests", args).get("isError").getAsBoolean());
            args.addProperty("limit", 0);
            assertTrue(server.callTool("get_current_quests", args).get("isError").getAsBoolean());
            args.addProperty("limit", 1);
            args.addProperty("offset", -1);
            assertTrue(server.callTool("get_current_quests", args).get("isError").getAsBoolean());
            game.set(FakeGameSource.MAIN_MENU);
            assertTrue(server.callTool("get_current_quests", new JsonObject()).get("isError").getAsBoolean());
        }
    }

    /**
     * Checks completing a quest removes it from the current list.
     * @throws Exception if the endpoint fails
     */
    @Test
    void completedBookHasNoCurrentQuests() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        FakeQuestSource source = new FakeQuestSource();
        source.set(new QuestBook(List.of(chapter("c", "Start", true,
                quest("q", "c", "Quest").status(QuestStatus.COMPLETED).build()))));
        try (McpTestServer server = new McpTestServer(game, source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            assertEquals("0 total, showing 0–0\n\nNo current quests.\n", text(server, new JsonObject()));
        }
    }

    /**
     * Extracts a successful current-quest result.
     * @param server the endpoint harness
     * @param args validated tool arguments
     * @return the Markdown output
     * @throws Exception if the endpoint fails
     */
    private static String text(McpTestServer server, JsonObject args) throws Exception {
        JsonObject result = server.callTool("get_current_quests", args);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }
}
