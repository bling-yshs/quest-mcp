package com.yshs.questmcp.core;

import static com.yshs.questmcp.core.FakeQuestSource.chapter;
import static com.yshs.questmcp.core.FakeQuestSource.item;
import static com.yshs.questmcp.core.FakeQuestSource.lines;
import static com.yshs.questmcp.core.FakeQuestSource.quest;
import static com.yshs.questmcp.core.FakeQuestSource.reward;
import static com.yshs.questmcp.core.FakeQuestSource.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.QuestBook.Reward;
import com.yshs.questmcp.core.source.QuestBook.Task;
import com.yshs.questmcp.core.source.QuestStatus;
import com.yshs.questmcp.core.source.RichText;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises quest search through the real HTTP endpoint with a neutral quest book. */
class SearchQuestsTest {
    private static final String CHAPTER = "1000000000000001";
    private static final String WOOD = "2000000000000001";
    private static final String SHOVEL = "2000000000000002";
    private static final String STASH = "2000000000000003";
    private static final String SECRET_CHAPTER = "1000000000000002";
    private static final String VAULT = "2000000000000004";

    private final FakeGameSource game = new FakeGameSource();
    private final FakeQuestSource quests = new FakeQuestSource();
    private final AtomicBoolean reveal = new AtomicBoolean();
    private final ExecutorService gameThread = Executors.newSingleThreadExecutor();
    private McpTestServer server;

    /**
     * Starts the real server with visible and hidden quests.
     *
     * @throws Exception if the endpoint cannot start
     */
    @BeforeEach
    void start() throws Exception {
        game.set(new GameStatus(true, true, true, "en_us"));
        RichText englishDescription = lines("A shovel lets you gather snowballs from snow.", "Bring them home.");
        quests.set(new QuestBook(List.of(
                chapter(CHAPTER, "Getting Started", true,
                        quest(WOOD, CHAPTER, "Gather Wood").status(QuestStatus.COMPLETED)
                                .tasks(task("ftbquests:item", "Oak Log", 4, 4,
                                        item("minecraft:oak_log", "Oak Log", 4))).build(),
                        quest(SHOVEL, CHAPTER, "Cold hands")
                                .subtitle("Winter equipment")
                                .description(englishDescription)
                                .status(QuestStatus.IN_PROGRESS)
                                .tasks(task("ftbquests:item", "Snowball", 2, 16,
                                        item("minecraft:snowball", "Snowball", 16)))
                                .rewards(reward("ftbquests:item", "Iron Shovel",
                                        item("minecraft:iron_shovel", "Iron Shovel", 1))).build(),
                        quest(STASH, CHAPTER, "Secret Stash").hidden()
                                .description(lines("snowballs hide here")).build()),
                chapter(SECRET_CHAPTER, "Developer Room", false,
                        quest(VAULT, SECRET_CHAPTER, "Snowball Vault").build()))));
        server = new McpTestServer(game, quests, reveal::get, gameThread, Duration.ofSeconds(5));
    }

    /** Stops the server and its fake game thread after each test. */
    @AfterEach
    void stop() {
        server.close();
        gameThread.shutdownNow();
    }

    /**
     * Checks the description-only snowball match and all required result fields.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void descriptionKeywordFindsQuestWithSnippetAndMetadata() throws Exception {
        String result = success("gather snowballs");
        assertTrue(result.startsWith("1 total, showing 1–1\n"), result);
        assertTrue(result.contains("- `" + SHOVEL + "` Cold hands (IN_PROGRESS) — Chapter: Getting Started (`"
                + CHAPTER + "`)"), result);
        assertTrue(result.contains("A shovel lets you gather snowballs from snow. Bring them home."), result);
    }

    /**
     * Checks title, subtitle, task IDs/names and reward IDs/names without case sensitivity.
     *
     * @param query the field-specific search text
     * @throws Exception if an endpoint request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"COLD HANDS", "winter EQUIPMENT", "MINECRAFT:SNOWBALL", "snowball", "minecraft:iron_shovel",
            "IRON SHOVEL", "  gather snowballs  "})
    void textAndReferencedItemsMatchIgnoringCase(String query) throws Exception {
        String result = success(query);
        assertTrue(result.startsWith("1 total, showing 1–1\n"), result);
        assertTrue(result.contains(SHOVEL), result);
        assertFalse(result.contains(STASH), result);
        assertFalse(result.contains(VAULT), result);
    }

    /**
     * Checks hidden quests, chapters and counts on both sides of the reveal switch.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void hiddenQuestsAndChaptersAreExcludedUnlessRevealed() throws Exception {
        assertTrue(success("snowball").startsWith("1 total, showing 1–1\n"));
        assertEquals("0 total, showing 0–0\n\nNo matching quests.\n", success("stash"));
        reveal.set(true);
        String result = success("snowball");
        assertTrue(result.startsWith("3 total, showing 1–3\n"), result);
        assertTrue(result.contains("`" + STASH + "` Secret Stash (AVAILABLE, hidden)"), result);
        assertTrue(result.contains("`" + VAULT + "` Snowball Vault (AVAILABLE, hidden)"), result);
        assertTrue(result.contains("revealHiddenQuests is on"), result);
    }

    /**
     * Checks that hidden descriptions, details, sequential tasks and rewards cannot produce hits.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void hiddenFieldsCannotMatchUnlessRevealed() throws Exception {
        quests.set(new QuestBook(List.of(chapter(CHAPTER, "Start", true,
                quest(WOOD, CHAPTER, "Wood").description(lines("secret formula")).descriptionHidden().build(),
                quest(SHOVEL, CHAPTER, "Shovel").detailsHidden()
                        .description(lines("secret formula"))
                        .tasks(task("ftbquests:item", "Secret", 0, 1, item("mod:secret", "Secret Formula", 1)))
                        .rewards(reward("ftbquests:item", "Secret", item("mod:secret", "Secret Formula", 1))).build(),
                quest(STASH, CHAPTER, "Stash")
                        .tasks(new Task("ftbquests:item", "Secret", List.of(item("mod:secret", "Secret", 1)),
                                0, 1, false, false))
                        .rewards(new Reward("ftbquests:item", "Secret",
                                List.of(item("mod:secret", "Secret", 1)), false)).build()))));
        assertTrue(success("secret").startsWith("0 total, showing 0–0\n"));
        assertTrue(success("mod:secret").startsWith("0 total, showing 0–0\n"));
        assertTrue(success("shovel").startsWith("1 total, showing 1–1\n"));
        reveal.set(true);
        assertTrue(success("secret").startsWith("3 total, showing 1–3\n"));
        assertTrue(success("mod:secret").startsWith("2 total, showing 1–2\n"));
    }

    /**
     * Checks matching against current display text and rejected language overrides.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void searchesCurrentDisplayedTextAndRejectsLanguageOverrides() throws Exception {
        game.set(new GameStatus(true, true, true, "zh_cn"));
        quests.set(new QuestBook(List.of(chapter(CHAPTER, "入门", true,
                quest(SHOVEL, CHAPTER, "冰冷的手").description(lines("用铲子从雪地采集雪球。"))
                        .status(QuestStatus.IN_PROGRESS).build()))));
        String chinese = success("雪球");
        assertTrue(chinese.contains("冰冷的手 (IN_PROGRESS) — Chapter: 入门"), chinese);
        assertTrue(chinese.contains("用铲子从雪地采集雪球。"), chinese);
        assertTrue(success("gather snowballs").startsWith("0 total"));
        JsonObject arguments = arguments("雪球");
        arguments.addProperty("lang", "en_us");
        JsonObject result = server.callTool("search_quests", arguments);
        assertTrue(result.get("isError").getAsBoolean(), result.toString());
    }

    /**
     * Checks visible link labels are searchable but destinations and image paths are not.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void richTextSearchUsesReadableLabels() throws Exception {
        RichText description = RichText.builder().text("First ")
                .questLink(WOOD, "old label").text("; ")
                .questLink(STASH, "a quiet place").text("; ")
                .urlLink("https://example.com/destination-only", "wiki label")
                .image("mod:textures/image-only.png").build();
        quests.set(new QuestBook(List.of(chapter(CHAPTER, "Start", true,
                quest(WOOD, CHAPTER, "Gather Wood").build(),
                quest(SHOVEL, CHAPTER, "Cold hands").description(description).build(),
                quest(STASH, CHAPTER, "Secret Stash").hidden().build()))));
        String result = success("wiki label");
        assertTrue(result.contains("First Gather Wood; a quiet place; wiki label"), result);
        assertFalse(result.contains("quest:"), result);
        assertFalse(result.contains("https://"), result);
        assertTrue(success("old label").startsWith("0 total"));
        assertTrue(success("destination-only").startsWith("0 total"));
        assertTrue(success("image-only").startsWith("0 total"));
        assertTrue(success("secret stash").startsWith("0 total"));
        reveal.set(true);
        assertTrue(success("secret stash").startsWith("2 total"));
    }

    /**
     * Checks a readable rumour may match without exposing the hidden quest it links to.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void aVisibleMentionOfASecretDoesNotExposeTheHiddenQuest() throws Exception {
        quests.set(new QuestBook(List.of(chapter(CHAPTER, "Start", true,
                quest(SHOVEL, CHAPTER, "Cold hands").description(RichText.builder()
                        .text("Rumour has it there is also ").questLink(STASH, "a secret stash").text(".")
                        .build()).build(),
                quest(STASH, CHAPTER, "Secret Stash").hidden().build()))));
        String result = success("secret stash");
        assertTrue(result.startsWith("1 total, showing 1–1\n"), result);
        assertTrue(result.contains(SHOVEL), result);
        assertTrue(result.contains("Rumour has it there is also a secret stash."), result);
        assertFalse(result.contains(STASH), result);
        reveal.set(true);
        assertTrue(success("secret stash").startsWith("2 total"));
    }

    /**
     * Checks snippets stay compact around late matches without splitting Unicode characters.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void longDescriptionsProduceBoundedSnippetsAroundTheMatch() throws Exception {
        quests.set(new QuestBook(List.of(chapter(CHAPTER, "Start", true,
                quest(SHOVEL, CHAPTER, "Cold hands")
                        .description(lines("😀".repeat(250) + "needle" + "尾".repeat(250))).build()))));
        String result = success("needle");
        String snippet = result.lines().filter(line -> line.startsWith("  ")).findFirst().orElseThrow().strip();
        assertTrue(snippet.startsWith("…"), result);
        assertTrue(snippet.endsWith("…"), result);
        assertTrue(snippet.contains("needle"), result);
        assertTrue(snippet.codePointCount(0, snippet.length()) <= 162, result);
        assertFalse(snippet.contains("\uFFFD"), result);
    }

    /**
     * Checks default size, stable order, last page and offsets beyond the result count.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void pagingCountsQuestsOnceAndPreservesBookOrder() throws Exception {
        List<Quest> entries = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            entries.add(quest(String.format("%016X", i + 1), CHAPTER, "Match " + i)
                    .description(lines("match again")).build());
        }
        quests.set(new QuestBook(List.of(new QuestBook.Chapter(CHAPTER, "Start", true, entries))));
        String first = success("match");
        assertTrue(first.startsWith("55 total, showing 1–10\n"), first);
        assertEquals(10, first.lines().filter(line -> line.startsWith("- ")).count());
        assertTrue(first.indexOf("Match 0 ") < first.indexOf("Match 9 "), first);
        JsonObject arguments = arguments("match");
        arguments.addProperty("limit", 50);
        assertEquals(50, success(arguments).lines().filter(line -> line.startsWith("- ")).count());
        arguments.addProperty("offset", 50);
        String last = success(arguments);
        assertTrue(last.startsWith("55 total, showing 51–55\n"), last);
        assertEquals(5, last.lines().filter(line -> line.startsWith("- ")).count());
        for (int offset : List.of(55, Integer.MAX_VALUE)) {
            arguments.addProperty("offset", offset);
            String empty = success(arguments);
            assertTrue(empty.startsWith("55 total, showing 0–0\n"), empty);
            assertTrue(empty.contains("No results at this offset"), empty);
        }
    }

    /**
     * Checks absent and blank queries, types, paging bounds and unknown arguments.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void invalidArgumentsAreToolErrors() throws Exception {
        List<JsonObject> invalid = new ArrayList<>();
        invalid.add(new JsonObject());
        invalid.add(arguments(" \n\t "));
        for (int limit : List.of(0, -1, 51)) {
            JsonObject arguments = arguments("snowball");
            arguments.addProperty("limit", limit);
            invalid.add(arguments);
        }
        for (long offset : List.of(-1L, 2147483648L)) {
            JsonObject arguments = arguments("snowball");
            arguments.addProperty("offset", offset);
            invalid.add(arguments);
        }
        JsonObject fractional = arguments("snowball");
        fractional.addProperty("offset", 1.5);
        invalid.add(fractional);
        JsonObject wrongType = arguments("snowball");
        wrongType.addProperty("query", 123);
        invalid.add(wrongType);
        JsonObject unknown = arguments("snowball");
        unknown.addProperty("extra", true);
        invalid.add(unknown);
        for (JsonObject arguments : invalid) {
            JsonObject result = server.callTool("search_quests", arguments);
            assertTrue(result.get("isError").getAsBoolean(), result.toString());
            assertFalse(McpTestServer.text(result).isBlank());
        }
    }

    /**
     * Checks a literal query is not interpreted as a regular expression.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void noResultsAndLiteralPunctuationAreSuccessfulEmptyPages() throws Exception {
        assertEquals("0 total, showing 0–0\n\nNo matching quests.\n", success("nonexistent"));
        assertTrue(success(".*").startsWith("0 total"));
    }

    /**
     * Checks a fluid reference is searched just like an item reference.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void fluidIdsAndGameLanguageItemNamesAreSearchable() throws Exception {
        quests.set(new QuestBook(List.of(chapter(CHAPTER, "Start", true,
                quest(SHOVEL, CHAPTER, "Cold hands")
                        .tasks(task("ftbquests:fluid", "Lava", 0, 1000,
                                FakeQuestSource.fluid("minecraft:lava", "熔岩", 1000)))
                        .rewards(reward("ftbquests:item", "Snowball", item("minecraft:snowball", "雪球", 1)))
                        .build()))));
        assertTrue(success("minecraft:lava").contains("熔岩 (`minecraft:lava`)"));
        assertTrue(success("熔岩").contains(SHOVEL));
        assertTrue(success("雪球").contains("雪球 (`minecraft:snowball`)"));
    }

    /**
     * Checks search is listed with its required query and bounded paging schema.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void toolsListAdvertisesSearchSchema() throws Exception {
        JsonObject result = server.request("tools/list", new JsonObject()).getAsJsonObject("result");
        JsonObject search = null;
        for (JsonElement element : result.getAsJsonArray("tools")) {
            if (element.getAsJsonObject().get("name").getAsString().equals("search_quests")) {
                search = element.getAsJsonObject();
            }
        }
        assertTrue(search != null, result.toString());
        JsonObject schema = search.getAsJsonObject("inputSchema");
        assertEquals("query", schema.getAsJsonArray("required").get(0).getAsString());
        assertEquals(50, schema.getAsJsonObject("properties").getAsJsonObject("limit").get("maximum").getAsInt());
        assertEquals(10, schema.getAsJsonObject("properties").getAsJsonObject("limit").get("default").getAsInt());
    }

    /**
     * Checks unavailable game states return helpful errors while JEI readiness is irrelevant.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void searchReportsGameStateErrorsButDoesNotRequireJei() throws Exception {
        game.set(FakeGameSource.MAIN_MENU);
        JsonObject menu = server.callTool("search_quests", arguments("snowball"));
        assertTrue(menu.get("isError").getAsBoolean());
        assertTrue(McpTestServer.text(menu).contains("not in a world"));
        game.set(new GameStatus(true, false, false, "en_us"));
        JsonObject loading = server.callTool("search_quests", arguments("snowball"));
        assertTrue(loading.get("isError").getAsBoolean());
        assertTrue(McpTestServer.text(loading).contains("has not been received"));
        game.set(new GameStatus(true, true, false, "en_us"));
        assertTrue(success("snowball").contains(SHOVEL));
    }

    /**
     * Calls search with validated success expected and extracts its single text block.
     *
     * @param arguments the search arguments
     * @return the successful Markdown result
     * @throws Exception if the endpoint request fails
     */
    private String success(JsonObject arguments) throws Exception {
        JsonObject result = server.callTool("search_quests", arguments);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }

    /**
     * Searches a query using default paging and the game language.
     *
     * @param query the literal search text
     * @return the successful Markdown result
     * @throws Exception if the endpoint request fails
     */
    private String success(String query) throws Exception {
        JsonObject result = server.callTool("search_quests", arguments(query));
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }

    /**
     * Creates the required search argument.
     *
     * @param query the literal search text
     * @return arguments containing the query
     */
    private static JsonObject arguments(String query) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("query", query);
        return arguments;
    }
}
