package com.yshs.questmcp.core;

import static com.yshs.questmcp.core.FakeQuestSource.chapter;
import static com.yshs.questmcp.core.FakeQuestSource.quest;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestSource.Scope;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Regressions for request-scoped game reads and current-language-only tool schemas. */
class QuestReadScopeTest {
    /**
     * Verifies each endpoint requests its own data scope and reads UI selection only when needed.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void dispatchesScopedReadsWithRequestedIdentityAndVisibility() throws Exception {
        String chapterId = "0000000000000001";
        String questId = "0000000000000002";
        FakeQuestSource source = new FakeQuestSource();
        source.set(new QuestBook(List.of(chapter(chapterId, "Start", true,
                quest(questId, chapterId, "Gather Wood").build()))));
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "zh_cn"));
        AtomicBoolean reveal = new AtomicBoolean();
        try (McpTestServer server = new McpTestServer(game, source, reveal::get, Runnable::run, Duration.ofSeconds(5))) {
            for (String tool : List.of("list_chapters", "get_current_quests", "get_chapter", "get_quest", "search_quests")) {
                JsonObject arguments = new JsonObject();
                Scope expected = Scope.OVERVIEW;
                String expectedId = "";
                switch (tool) {
                    case "get_chapter" -> {
                        arguments.addProperty("chapter_id", chapterId);
                        expected = Scope.CHAPTER;
                        expectedId = chapterId;
                    }
                    case "get_quest" -> {
                        arguments.addProperty("quest_id", questId);
                        expected = Scope.QUEST;
                        expectedId = questId;
                    }
                    case "search_quests" -> {
                        arguments.addProperty("query", "Wood");
                        expected = Scope.SEARCH;
                    }
                    default -> { }
                }
                JsonObject result = server.callTool(tool, arguments);
                assertFalse(result.get("isError").getAsBoolean(), result.toString());
                assertEquals(expected, source.lastScope, tool);
                assertEquals(expectedId, source.lastId, tool);
                assertFalse(source.lastRevealHidden, tool);
                assertEquals(0, source.viewedReads, tool);
            }
            source.viewedQuestId = Optional.of(questId);
            reveal.set(true);
            assertFalse(server.callTool("get_viewed_quest", new JsonObject()).get("isError").getAsBoolean());
            assertEquals(Scope.VIEWED, source.lastScope);
            assertEquals(questId, source.lastId);
            assertTrue(source.lastRevealHidden);
            assertEquals(1, source.viewedReads);
            source.viewedQuestId = Optional.empty();
            assertTrue(McpTestServer.text(server.callTool("get_viewed_quest", new JsonObject())).contains("Viewed quest: none"));
            assertEquals("", source.lastId);
            assertEquals(2, source.viewedReads);
        }
    }

    /**
     * Verifies readiness errors occur before quest or active-screen reads.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void skipsSourceReadsUntilTheQuestBookIsReady() throws Exception {
        FakeGameSource game = new FakeGameSource();
        FakeQuestSource source = new FakeQuestSource();
        try (McpTestServer server = new McpTestServer(game, source, () -> false, Runnable::run, Duration.ofSeconds(5))) {
            for (GameStatus status : List.of(FakeGameSource.MAIN_MENU, new GameStatus(true, false, true, "zh_cn"))) {
                game.set(status);
                assertTrue(server.callTool("get_viewed_quest", new JsonObject()).get("isError").getAsBoolean());
                assertNull(source.lastScope);
                assertEquals(0, source.viewedReads);
            }
        }
    }

    /**
     * Verifies discovery and argument validation consistently remove language overrides from every tool.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void everyToolUsesCurrentLanguageWithoutALanguageArgument() throws Exception {
        try (McpTestServer server = new McpTestServer(new FakeGameSource(), Runnable::run, Duration.ofSeconds(5))) {
            var tools = server.request("tools/list", new JsonObject()).getAsJsonObject("result").getAsJsonArray("tools");
            for (var element : tools) {
                JsonObject tool = element.getAsJsonObject();
                String name = tool.get("name").getAsString();
                assertFalse(tool.getAsJsonObject("inputSchema").getAsJsonObject("properties").has("lang"), name);
                JsonObject arguments = new JsonObject();
                arguments.addProperty("lang", "en_us");
                JsonObject result = server.callTool(name, arguments);
                assertTrue(result.get("isError").getAsBoolean(), name);
                assertTrue(McpTestServer.text(result).contains("unknown argument \"lang\""), name);
            }
        }
    }
}
