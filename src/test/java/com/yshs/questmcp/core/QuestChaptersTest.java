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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code list_chapters} and {@code get_chapter}: chapters and quests, visibility, language and game state. */
class QuestChaptersTest {
    private static final String START = "0000000000000A01";
    private static final String NETHER = "0000000000000B01";
    private static final String SECRET_CHAPTER = "0000000000000C01";
    private static final String WOOD = "0000000000000101";
    private static final String SNOWBALLS = "0000000000000102";
    private static final String GOLEM = "0000000000000103";
    private static final String IRON = "0000000000000104";
    private static final String STASH = "0000000000000105";
    private static final String PORTAL = "0000000000000201";
    private static final String VAULT = "0000000000000301";

    /**
     * Two visible chapters, one hidden chapter, and a hidden quest, using the current English display text.
     */
    private static final QuestBook BOOK = new QuestBook(List.of(
            chapter(START, "Getting Started", true,
                    quest(WOOD, START, "Gather Wood", "",
                            true, QuestStatus.COMPLETED),
                    quest(SNOWBALLS, START, "Make Snowballs",
                            "Cold hands",
                            true, QuestStatus.AVAILABLE, WOOD),
                    quest(GOLEM, START, "Build a Snow Golem", "", true, QuestStatus.IN_PROGRESS,
                            SNOWBALLS),
                    quest(IRON, START, "Iron Ingot", "", true, QuestStatus.LOCKED, GOLEM),
                    quest(STASH, START, "Secret Stash", "",
                            false, QuestStatus.LOCKED, IRON)),
            chapter(NETHER, "The Nether", true,
                    quest(PORTAL, NETHER, "Light a Portal", "", true, QuestStatus.LOCKED, IRON)),
            // A chapter the quest book does not list (e.g. always invisible), with a quest that is not hidden itself.
            chapter(SECRET_CHAPTER, "Developer Room", false,
                    quest(VAULT, SECRET_CHAPTER, "Open the Vault", "", true, QuestStatus.AVAILABLE))));

    private final FakeGameSource game = new FakeGameSource();
    private final FakeQuestSource quests = new FakeQuestSource();
    private final AtomicBoolean revealHiddenQuests = new AtomicBoolean();
    private final ExecutorService gameThread = Executors.newSingleThreadExecutor();
    private McpTestServer server;

    @BeforeEach
    void start() throws Exception {
        game.set(new GameStatus(true, true, true, "en_us"));
        quests.set(BOOK);
        server = new McpTestServer(game, quests, revealHiddenQuests::get, gameThread, Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        server.close();
        gameThread.shutdownNow();
    }

    @Test
    void listChaptersShowsEachVisibleChapterWithItsCompletion() throws Exception {
        String text = success("list_chapters", new JsonObject());

        assertTrue(text.contains("2 chapters."), text);
        // The hidden quest does not count: 1 of the 4 visible quests is completed.
        assertTrue(text.contains("- `" + START + "` Getting Started: 4 quests, 1 completed (25%)\n"), text);
        assertTrue(text.contains("- `" + NETHER + "` The Nether: 1 quest, 0 completed (0%)\n"), text);
        assertTrue(text.indexOf(START) < text.indexOf(NETHER), "chapters are in quest book order: " + text);
    }

    @Test
    void listChaptersLeavesOutHiddenChaptersWithoutHintingAtThem() throws Exception {
        String text = success("list_chapters", new JsonObject());

        assertFalse(text.contains(SECRET_CHAPTER), text);
        assertFalse(text.contains("Developer Room"), text);
        assertFalse(text.toLowerCase().contains("hidden"), text);
    }

    @Test
    void listChaptersIncludesAndMarksHiddenChaptersWhenRevealed() throws Exception {
        revealHiddenQuests.set(true);

        String text = success("list_chapters", new JsonObject());

        assertTrue(text.contains("3 chapters."), text);
        assertTrue(text.contains("revealHiddenQuests is on"), text);
        assertTrue(text.contains("- `" + START + "` Getting Started: 5 quests, 1 completed (20%)\n"), text);
        assertTrue(text.contains("- `" + SECRET_CHAPTER + "` Developer Room (hidden): 1 quest, 0 completed (0%)\n"),
                text);
    }

    @Test
    void listChaptersOfAnEmptyQuestBook() throws Exception {
        quests.set(new QuestBook(List.of()));

        assertTrue(success("list_chapters", new JsonObject()).contains("The quest book has no chapters."));
    }

    @Test
    void getChapterListsTheQuestsWithStatusAndSubtitle() throws Exception {
        String text = success("get_chapter", chapterId(START));

        assertTrue(text.startsWith("# Getting Started (`" + START + "`)\n"), text);
        assertTrue(text.contains("4 quests, 1 completed (25%)."), text);
        assertTrue(text.contains("- `" + WOOD + "` Gather Wood (COMPLETED)\n"), text);
        assertTrue(text.contains("- `" + SNOWBALLS + "` Make Snowballs (AVAILABLE): Cold hands\n"), text);
        assertTrue(text.contains("- `" + GOLEM + "` Build a Snow Golem (IN_PROGRESS)\n"), text);
        assertTrue(text.contains("- `" + IRON + "` Iron Ingot (LOCKED)\n"), text);
        assertTrue(text.indexOf(WOOD) < text.indexOf(SNOWBALLS) && text.indexOf(GOLEM) < text.indexOf(IRON),
                "quests are in quest book order: " + text);
    }

    @Test
    void getChapterLeavesOutHiddenQuests() throws Exception {
        String text = success("get_chapter", chapterId(START));

        assertFalse(text.contains(STASH), text);
        assertFalse(text.contains("Secret Stash"), text);
        assertFalse(text.toLowerCase().contains("hidden"), text);
    }

    @Test
    void getChapterIncludesAndMarksHiddenQuestsWhenRevealed() throws Exception {
        revealHiddenQuests.set(true);

        String text = success("get_chapter", chapterId(START));

        assertTrue(text.contains("5 quests, 1 completed (20%)."), text);
        assertTrue(text.contains("revealHiddenQuests is on"), text);
        assertTrue(text.contains("- `" + STASH + "` Secret Stash (LOCKED, hidden)\n"), text);
        assertTrue(text.contains("- `" + WOOD + "` Gather Wood (COMPLETED)\n"), "visible quests are not marked: "
                + text);
    }

    @Test
    void aHiddenChapterIsNotFoundUnlessRevealed() throws Exception {
        JsonObject hidden = server.callTool("get_chapter", chapterId(SECRET_CHAPTER));

        assertTrue(hidden.get("isError").getAsBoolean(), hidden.toString());
        assertEquals(notFound(SECRET_CHAPTER), McpTestServer.text(hidden));

        revealHiddenQuests.set(true);
        String text = success("get_chapter", chapterId(SECRET_CHAPTER));

        assertTrue(text.startsWith("# Developer Room (`" + SECRET_CHAPTER + "`) (hidden)\n"), text);
        // Not hidden by itself, but nobody can see it in a chapter the quest book does not list.
        assertTrue(text.contains("- `" + VAULT + "` Open the Vault (AVAILABLE, hidden)\n"), text);
    }

    @Test
    void anUnknownChapterIsAnErrorPointingToListChapters() throws Exception {
        JsonObject result = server.callTool("get_chapter", chapterId("0123456789ABCDEF"));

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertEquals(notFound("0123456789ABCDEF"), McpTestServer.text(result));
        assertTrue(McpTestServer.text(result).contains("list_chapters"), result.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000000000000a01", "#0000000000000A01", " 0000000000000A01 "})
    void chapterIdsMatchIgnoringCaseAndALeadingHash(String id) throws Exception {
        assertTrue(success("get_chapter", chapterId(id)).startsWith("# Getting Started (`" + START + "`)"));
    }

    /**
     * Checks the required chapter identity is validated.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void getChapterRequiresAChapterId() throws Exception {
        JsonObject result = server.callTool("get_chapter", new JsonObject());

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertTrue(McpTestServer.text(result).contains("missing required argument \"chapter_id\""), result.toString());
    }

    /**
     * Checks uses current displayed chapter and quest text.
     * @throws Exception if the operation fails
     */
    @Test
    void usesCurrentDisplayedChapterAndQuestText() throws Exception {
        game.set(new GameStatus(true, true, true, "zh_cn"));
        quests.set(new QuestBook(List.of(chapter(START, "入门", true,
                quest(SNOWBALLS, START, "制作雪球", "冰冷的手", true, QuestStatus.AVAILABLE)),
                chapter(NETHER, "The Nether", true))));
        String chapters = success("list_chapters", new JsonObject());
        String chapter = success("get_chapter", chapterId(START));
        assertTrue(chapters.contains("`" + START + "` 入门: "), chapters);
        assertTrue(chapters.contains("`" + NETHER + "` The Nether: "), chapters);
        assertTrue(chapter.contains("制作雪球 (AVAILABLE): 冰冷的手"), chapter);
        assertFalse(chapter.contains("No quest text in"), chapter);
        quests.set(BOOK);
        game.set(new GameStatus(true, true, true, "en_us"));
        assertTrue(success("get_chapter", chapterId(START)).contains("Make Snowballs"));
    }

    /**
     * Checks rejects language overrides for chapter tools.
     * @throws Exception if the operation fails
     */
    @Test
    void rejectsLanguageOverridesForChapterTools() throws Exception {
        for (String tool : List.of("list_chapters", "get_chapter")) {
            JsonObject arguments = tool.equals("get_chapter") ? chapterId(START) : new JsonObject();
            arguments.addProperty("lang", "en_us");
            JsonObject result = server.callTool(tool, arguments);
            assertTrue(result.get("isError").getAsBoolean(), result.toString());
            assertTrue(McpTestServer.text(result).contains("unknown argument \"lang\""));
        }
    }

    /**
     * Checks preserves literal displayed text.
     * @throws Exception if the operation fails
     */
    @Test
    void preservesLiteralDisplayedText() throws Exception {
        quests.set(new QuestBook(List.of(chapter(START, "自定义章节", true,
                quest(SNOWBALLS, START, "Pack supplied title", "", true, QuestStatus.AVAILABLE)))));
        String text = success("get_chapter", chapterId(START));
        assertTrue(text.contains("自定义章节"), text);
        assertTrue(text.contains("Pack supplied title"), text);
        assertFalse(text.contains("No quest text in"), text);
    }

    @Test
    void questToolsExplainThatThePlayerIsNotInAWorld() throws Exception {
        game.set(FakeGameSource.MAIN_MENU);

        for (JsonObject result : List.of(server.callTool("list_chapters", new JsonObject()),
                server.callTool("get_chapter", chapterId(START)))) {
            assertTrue(result.get("isError").getAsBoolean(), result.toString());
            assertTrue(McpTestServer.text(result).contains("not in a world"), result.toString());
        }
    }

    @Test
    void questToolsExplainThatTheQuestBookHasNotArrived() throws Exception {
        game.set(new GameStatus(true, false, true, "en_us"));

        for (JsonObject result : List.of(server.callTool("list_chapters", new JsonObject()),
                server.callTool("get_chapter", chapterId(START)))) {
            assertTrue(result.get("isError").getAsBoolean(), result.toString());
            assertTrue(McpTestServer.text(result).contains("quest book has not been received"), result.toString());
        }
    }

    @Test
    void questToolsDoNotNeedJei() throws Exception {
        game.set(new GameStatus(true, true, false, "en_us"));

        assertTrue(success("list_chapters", new JsonObject()).contains("Getting Started"));
    }

    @Test
    void getStatusReportsTheRevealHiddenQuestsSetting() throws Exception {
        assertTrue(success("get_status", new JsonObject()).contains("revealHiddenQuests: `false`"));

        revealHiddenQuests.set(true);

        assertTrue(success("get_status", new JsonObject()).contains("revealHiddenQuests: `true`"));
    }

    private String success(String tool, JsonObject arguments) throws Exception {
        JsonObject result = server.callTool(tool, arguments);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }

    private static String notFound(String id) {
        return "No chapter with ID `" + id + "` exists. Call list_chapters to get the IDs of the chapters.";
    }

    /**
     * Builds a chapter lookup request.
     * @param id requested chapter ID
     * @return tool arguments
     */
    private static JsonObject chapterId(String id) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("chapter_id", id);
        return arguments;
    }

}
