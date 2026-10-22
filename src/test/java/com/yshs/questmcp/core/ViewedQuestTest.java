package com.yshs.questmcp.core;

import static com.yshs.questmcp.core.FakeQuestSource.chapter;
import static com.yshs.questmcp.core.FakeQuestSource.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Checks live quest-book selection through the real MCP endpoint. */
class ViewedQuestTest {
    private static final String CHAPTER = "1000000000000001";
    private static final String FIRST = "2000000000000001";
    private static final String SECOND = "2000000000000002";
    private static final String HIDDEN = "2000000000000003";
    private static final String HIDDEN_CHAPTER = "1000000000000002";
    private static final String VAULT = "2000000000000004";

    /**
     * Creates a bilingual quest book with a hidden quest and hidden chapter.
     *
     * @return fake quest data with no active selection
     */
    private static FakeQuestSource source() {
        FakeQuestSource source = new FakeQuestSource();
        source.set(new QuestBook(List.of(
                chapter(CHAPTER, "Start", true,
                        quest(FIRST, CHAPTER, "Gather Wood")
                                .status(QuestStatus.IN_PROGRESS).build(),
                        quest(SECOND, CHAPTER, "Make Snowballs").status(QuestStatus.LOCKED).build(),
                        quest(HIDDEN, CHAPTER, "Secret Stash").hidden().build()),
                chapter(HIDDEN_CHAPTER, "Developer Room", false,
                        quest(VAULT, HIDDEN_CHAPTER, "Debug Quest").build()))));
        return source;
    }

    /**
     * Creates a game with loaded quests independently of JEI readiness.
     *
     * @return ready quest game state
     */
    private static FakeGameSource game() {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, false, "en_us"));
        return game;
    }

    /**
     * Checks the returned ID, localized title, chapter, status and get_quest guidance.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void reportsTheQuestCurrentlyOpenInDetails() throws Exception {
        FakeQuestSource source = source();
        source.viewedQuestId = Optional.of(FIRST);
        try (McpTestServer server = new McpTestServer(game(), source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            String result = success(server, new JsonObject());
            assertTrue(result.contains("- Quest: `" + FIRST + "` Gather Wood"), result);
            assertTrue(result.contains("- Status: IN_PROGRESS"), result);
            assertTrue(result.contains("- Chapter: `" + CHAPTER + "` Start"), result);
            assertTrue(result.contains("Call get_quest with quest_id `" + FIRST + "`"), result);
            assertFalse(result.contains(SECOND), result);
        }
    }

    /**
     * Checks switching and closing details cannot return a previously viewed quest.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void readsSelectionChangesAndDoesNotCacheThePreviousQuest() throws Exception {
        FakeQuestSource source = source();
        try (McpTestServer server = new McpTestServer(game(), source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            String none = success(server, new JsonObject());
            assertTrue(none.contains("- Viewed quest: none"), none);
            source.viewedQuestId = Optional.of(FIRST);
            assertTrue(success(server, new JsonObject()).contains(FIRST));
            source.viewedQuestId = Optional.of(SECOND);
            String switched = success(server, new JsonObject());
            assertTrue(switched.contains(SECOND), switched);
            assertTrue(switched.contains("- Status: LOCKED"), switched);
            assertFalse(switched.contains(FIRST), switched);
            source.viewedQuestId = Optional.empty();
            assertEquals(none, success(server, new JsonObject()));
        }
    }

    /**
     * Checks a stale selection after quest-book replacement does not expose its ID.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void staleOrUnknownSelectionIsTheSameAsNoSelection() throws Exception {
        FakeQuestSource source = source();
        try (McpTestServer server = new McpTestServer(game(), source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            String none = success(server, new JsonObject());
            source.viewedQuestId = Optional.of("missing");
            assertEquals(none, success(server, new JsonObject()));
            source.viewedQuestId = Optional.of(FIRST);
            source.set(new QuestBook(List.of()));
            assertEquals(none, success(server, new JsonObject()));
        }
    }

    /**
     * Checks edit-mode hidden selections and hidden chapters respect the reveal switch.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void hiddenSelectionsCannotBypassVisibilityRules() throws Exception {
        FakeQuestSource source = source();
        AtomicBoolean reveal = new AtomicBoolean();
        try (McpTestServer server = new McpTestServer(game(), source, reveal::get, Runnable::run, Duration.ofSeconds(5))) {
            String none = success(server, new JsonObject());
            for (String id : List.of(HIDDEN, VAULT)) {
                source.viewedQuestId = Optional.of(id);
                assertEquals(none, success(server, new JsonObject()));
                reveal.set(true);
                String shown = success(server, new JsonObject());
                assertTrue(shown.contains(id), shown);
                assertTrue(shown.contains("(hidden)"), shown);
                assertTrue(shown.contains("revealHiddenQuests is on"), shown);
                reveal.set(false);
            }
        }
    }

    /**
     * Checks current display text, language changes and rejected language overrides.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void usesCurrentDisplayedSelectionAndRejectsLanguageOverrides() throws Exception {
        FakeGameSource game = game();
        game.set(new GameStatus(true, true, false, "zh_cn"));
        FakeQuestSource source = source();
        source.set(new QuestBook(List.of(chapter(CHAPTER, "入门", true,
                quest(FIRST, CHAPTER, "收集木头").build()))));
        source.viewedQuestId = Optional.of(FIRST);
        try (McpTestServer server = new McpTestServer(game, source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            assertTrue(success(server, new JsonObject()).contains("收集木头"));
            JsonObject args = new JsonObject();
            args.addProperty("lang", "en_us");
            assertTrue(server.callTool("get_viewed_quest", args).get("isError").getAsBoolean());
            source.set(new QuestBook(List.of(chapter(CHAPTER, "Start", true,
                    quest(FIRST, CHAPTER, "Gather Wood").build()))));
            assertTrue(success(server, new JsonObject()).contains("Gather Wood"));
        }
    }

    /**
     * Checks world and quest synchronization errors rather than treating them as an empty selection.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void reportsUnavailableGameAndQuestBook() throws Exception {
        FakeGameSource game = game();
        try (McpTestServer server = new McpTestServer(game, source(), () -> false, Runnable::run, Duration.ofSeconds(5))) {
            game.set(FakeGameSource.MAIN_MENU);
            JsonObject menu = server.callTool("get_viewed_quest", new JsonObject());
            assertTrue(menu.get("isError").getAsBoolean());
            assertTrue(McpTestServer.text(menu).contains("not in a world"));
            game.set(new GameStatus(true, false, false, "en_us"));
            JsonObject loading = server.callTool("get_viewed_quest", new JsonObject());
            assertTrue(loading.get("isError").getAsBoolean());
            assertTrue(McpTestServer.text(loading).contains("has not been received"));
        }
    }

    /**
     * Checks the new tool schema and invalid arguments through discovery and calls.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void advertisesEmptyArgumentsAndRejectsUnexpectedArguments() throws Exception {
        try (McpTestServer server = new McpTestServer(game(), source(), () -> false, Runnable::run, Duration.ofSeconds(5))) {
            var tools = server.request("tools/list", new JsonObject()).getAsJsonObject("result").getAsJsonArray("tools");
            JsonObject viewed = null;
            for (var element : tools) {
                if (element.getAsJsonObject().get("name").getAsString().equals("get_viewed_quest")) {
                    viewed = element.getAsJsonObject();
                }
            }
            assertTrue(viewed != null, tools.toString());
            assertTrue(viewed.get("description").getAsString().contains("this quest"));
            var schema = viewed.getAsJsonObject("inputSchema");
            assertFalse(schema.has("required"), schema.toString());
            assertEquals(java.util.Set.of(), schema.getAsJsonObject("properties").keySet());
            JsonObject bad = new JsonObject();
            bad.addProperty("quest_id", FIRST);
            assertTrue(server.callTool("get_viewed_quest", bad).get("isError").getAsBoolean());
            bad = new JsonObject();
            bad.addProperty("lang", 1);
            assertTrue(server.callTool("get_viewed_quest", bad).get("isError").getAsBoolean());
        }
    }

    /**
     * Checks unresponsive game-thread reads preserve the existing timeout error.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void timesOutWithoutGuessingAViewedQuest() throws Exception {
        try (McpTestServer server = new McpTestServer(game(), source(), () -> false, task -> { }, Duration.ofMillis(20))) {
            JsonObject result = server.callTool("get_viewed_quest", new JsonObject());
            assertTrue(result.get("isError").getAsBoolean());
            assertTrue(McpTestServer.text(result).contains("did not respond"));
        }
    }

    /**
     * Extracts a successful viewed-quest text result.
     *
     * @param server real endpoint harness
     * @param args tool arguments
     * @return Markdown quest context
     * @throws Exception if an endpoint request fails
     */
    private static String success(McpTestServer server, JsonObject args) throws Exception {
        JsonObject result = server.callTool("get_viewed_quest", args);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }
}
