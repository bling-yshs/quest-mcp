package com.yshs.questmcp.core;

import static com.yshs.questmcp.core.FakeQuestSource.chapter;
import static com.yshs.questmcp.core.FakeQuestSource.fluid;
import static com.yshs.questmcp.core.FakeQuestSource.item;
import static com.yshs.questmcp.core.FakeQuestSource.lines;
import static com.yshs.questmcp.core.FakeQuestSource.quest;
import static com.yshs.questmcp.core.FakeQuestSource.reward;
import static com.yshs.questmcp.core.FakeQuestSource.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestBook.Reward;
import com.yshs.questmcp.core.source.QuestBook.Task;
import com.yshs.questmcp.core.source.QuestStatus;
import com.yshs.questmcp.core.source.RichText;
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

/** {@code get_quest}: a quest in full, its description as Markdown, and what the quest book hides. */
class GetQuestTest {
    private static final String START = "0000000000000A01";
    private static final String NETHER = "0000000000000B01";
    private static final String SECRET_CHAPTER = "0000000000000C01";
    private static final String WOOD = "0000000000000101";
    private static final String SNOWBALLS = "00000000000000B2";
    private static final String GOLEM = "0000000000000103";
    private static final String PIE = "0000000000000104";
    private static final String STASH = "0000000000000105";
    private static final String ORDERED = "0000000000000106";
    private static final String MIXED = "0000000000000107";
    private static final String PORTAL = "0000000000000201";
    private static final String VAULT = "0000000000000301";

    /** Two paragraphs, a link to a visible and to a hidden quest, a web link, a page break and an image. */
    private static final RichText SNOWBALLS_EN = RichText.builder()
            .text("Snowballs are the key ingredient of the snow golem.").lineBreak()
            .text("You need a shovel.").lineBreak()
            .lineBreak()
            .text("First finish ").questLink(WOOD, "the wood quest").text(", then read ")
            .questLink(STASH, "the secret note").text(".").lineBreak()
            .text("Wiki: ").urlLink("https://minecraft.wiki/w/Snowball", "Snowball page").lineBreak()
            .paragraphBreak()
            .image("minecraft:textures/item/snowball.png").lineBreak()
            .build();
    private static final RichText SNOWBALLS_ZH = RichText.builder()
            .text("雪球是雪傀儡的关键材料。").lineBreak()
            .text("先完成").questLink(WOOD, "木头任务").text("。").lineBreak()
            .build();

    private static final QuestBook BOOK = new QuestBook(List.of(
            chapter(START, "Getting Started", true,
                    quest(WOOD, START, "Gather Wood")
                            .status(QuestStatus.COMPLETED)
                            .tasks(task("ftbquests:item", "4x Oak Log", 4, 4, item("minecraft:oak_log", "Oak Log", 4)))
                            .rewards(reward("ftbquests:item", "2x Apple", item("minecraft:apple", "Apple", 2)))
                            .build(),
                    quest(SNOWBALLS, START, "Make Snowballs")
                            .subtitle("Cold hands")
                            .description(SNOWBALLS_EN)
                            .status(QuestStatus.IN_PROGRESS)
                            .dependencies(WOOD)
                            .tasks(task("ftbquests:item", "16x Snowball", 12, 16,
                                    item("minecraft:snowball", "Snowball", 16)))
                            .rewards(reward("ftbquests:item", "Iron Shovel",
                                    item("minecraft:iron_shovel", "Iron Shovel", 1)))
                            .build(),
                    // "Hide details until startable" while it cannot be started.
                    quest(GOLEM, START, "Build a Snow Golem")
                            .description(lines("Stack two snow blocks and add a pumpkin."))
                            .detailsHidden()
                            .status(QuestStatus.LOCKED)
                            .dependencies(SNOWBALLS)
                            .tasks(task("ftbquests:item", "Carved Pumpkin", 0, 1,
                                    item("minecraft:carved_pumpkin", "Carved Pumpkin", 1)))
                            .rewards(reward("ftbquests:item", "3x Pumpkin Pie",
                                    item("minecraft:pumpkin_pie", "Pumpkin Pie", 3)))
                            .build(),
                    // "Hide text until complete" while it is not completed.
                    quest(PIE, START, "Bake a Pie")
                            .description(lines("The secret recipe: pumpkin, sugar and an egg."))
                            .descriptionHidden()
                            .dependencies(WOOD)
                            .tasks(task("ftbquests:item", "3x Pumpkin", 0, 3, item("minecraft:pumpkin", "Pumpkin", 3)))
                            .build(),
                    quest(STASH, START, "Secret Stash")
                            .description(lines("You found the stash."))
                            .hidden()
                            .dependencies(SNOWBALLS)
                            .build(),
                    // Tasks that must be done in order, and a reward the quest book does not list.
                    quest(ORDERED, START, "One Step at a Time")
                            .status(QuestStatus.IN_PROGRESS)
                            .tasks(task("ftbquests:item", "Stone Pickaxe", 1, 1,
                                            item("minecraft:stone_pickaxe", "Stone Pickaxe", 1)),
                                    task("ftbquests:item", "5x Iron Ore", 2, 5,
                                            item("minecraft:iron_ore", "Iron Ore", 5)),
                                    new Task("ftbquests:kill", "Kill a Zombie", List.of(), 0, 1, false,
                                            false))
                            .rewards(reward("ftbquests:item", "Apple", item("minecraft:apple", "Apple", 1)),
                                    new Reward("ftbquests:item", "Secret Diamond",
                                            List.of(item("minecraft:diamond", "Diamond", 1)), false))
                            .build(),
                    // Task and reward types without items, a fluid, and an item filter accepting several items.
                    quest(MIXED, START, "Odds and Ends")
                            .status(QuestStatus.IN_PROGRESS)
                            .tasks(task("ftbquests:item", "8x Any Log", 3, 8,
                                            item("minecraft:oak_log", "Oak Log", 8),
                                            item("minecraft:birch_log", "Birch Log", 8),
                                            item("minecraft:spruce_log", "Spruce Log", 8)),
                                    task("ftbquests:fluid", "1 B of Water", 500, 1000,
                                            fluid("minecraft:water", "Water", 1000)),
                                    task("ftbquests:checkmark", "Read the manual", 0, 1),
                                    task("othermod:mana", "Gather Mana", 40, 100))
                            .rewards(reward("ftbquests:xp", "100 XP"), reward("othermod:thing", "A Thing"))
                            .build()),
            chapter(NETHER, "The Nether", true,
                    quest(PORTAL, NETHER, "Light a Portal").status(QuestStatus.LOCKED).dependencies(WOOD)
                            .build()),
            chapter(SECRET_CHAPTER, "Developer Room", false,
                    quest(VAULT, SECRET_CHAPTER, "Open the Vault").dependencies(WOOD).build())));

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
    void getQuestShowsTitleSubtitleStatusAndChapter() throws Exception {
        String text = success(SNOWBALLS);

        assertTrue(text.startsWith("# Make Snowballs (`" + SNOWBALLS + "`)\n\n"
                + "- Subtitle: Cold hands\n"
                + "- Status: IN_PROGRESS\n"
                + "- Chapter: Getting Started (`" + START + "`)\n"), text);
    }

    @Test
    void theDescriptionIsMarkdownWithParagraphsLinksAndImages() throws Exception {
        String text = success(SNOWBALLS);

        assertTrue(text.contains("## Description\n\n"
                + "Snowballs are the key ingredient of the snow golem.\n"
                + "You need a shovel.\n"
                + "\n"
                + "First finish [Gather Wood](quest:" + WOOD + "), then read the secret note.\n"
                + "Wiki: [Snowball page](https://minecraft.wiki/w/Snowball)\n"
                + "\n"
                + "[image: minecraft:textures/item/snowball.png]\n"), text);
    }

    @Test
    void aLinkToAHiddenQuestIsPlainTextUnlessRevealed() throws Exception {
        String hidden = success(SNOWBALLS);

        assertTrue(hidden.contains("then read the secret note."), hidden);
        assertFalse(hidden.contains(STASH), hidden);
        assertFalse(hidden.contains("Secret Stash"), hidden);

        revealHiddenQuests.set(true);
        String revealed = success(SNOWBALLS);

        assertTrue(revealed.contains("then read [Secret Stash](quest:" + STASH + ")."), revealed);
    }

    @Test
    void tasksShowTheirItemsAndTheTeamsProgress() throws Exception {
        String snowballs = success(SNOWBALLS);
        String wood = success(WOOD);

        assertTrue(snowballs.contains("## Tasks\n\n"
                + "- 16x Snowball (`ftbquests:item`): 16 × Snowball (`minecraft:snowball`). Progress: 12/16\n"),
                snowballs);
        assertTrue(wood.contains("- 4x Oak Log (`ftbquests:item`): 4 × Oak Log (`minecraft:oak_log`). "
                + "Progress: 4/4 (done)\n"), wood);
    }

    @Test
    void rewardsShowTheirItems() throws Exception {
        String text = success(SNOWBALLS);

        assertTrue(text.contains("## Rewards\n\n- Iron Shovel (`ftbquests:item`): 1 × Iron Shovel "
                + "(`minecraft:iron_shovel`)\n"), text);
    }

    @Test
    void otherTaskAndRewardTypesShowTheirTypeAndTitle() throws Exception {
        String text = success(MIXED);

        assertTrue(text.contains("- 8x Any Log (`ftbquests:item`): 8 × any of Oak Log (`minecraft:oak_log`), "
                + "Birch Log (`minecraft:birch_log`), Spruce Log (`minecraft:spruce_log`). Progress: 3/8\n"), text);
        assertTrue(text.contains("- 1 B of Water (`ftbquests:fluid`): 1000 mB × Water (`minecraft:water`). "
                + "Progress: 500/1000\n"), text);
        assertTrue(text.contains("- Read the manual (`ftbquests:checkmark`). Progress: 0/1\n"), text);
        assertTrue(text.contains("- Gather Mana (`othermod:mana`). Progress: 40/100\n"), text);
        assertTrue(text.contains("## Rewards\n\n- 100 XP (`ftbquests:xp`)\n- A Thing (`othermod:thing`)\n"), text);
    }

    @Test
    void prerequisitesAndFollowUpsShowIdTitleAndStatus() throws Exception {
        String snowballs = success(SNOWBALLS);
        String wood = success(WOOD);

        assertTrue(snowballs.contains("## Prerequisites\n\n- `" + WOOD + "` Gather Wood (COMPLETED)\n"), snowballs);
        assertTrue(snowballs.contains("## Follow-ups\n\n- `" + GOLEM + "` Build a Snow Golem (LOCKED)\n"),
                snowballs);
        assertTrue(wood.contains("## Prerequisites\n\nNone.\n"), wood);
        assertTrue(wood.contains("## Follow-ups\n\n"
                + "- `" + SNOWBALLS + "` Make Snowballs (IN_PROGRESS)\n"
                + "- `" + PIE + "` Bake a Pie (AVAILABLE)\n"
                + "- `" + PORTAL + "` Light a Portal (LOCKED, chapter The Nether)\n"), wood);
    }

    /**
     * Checks follow-up visibility before and after revealing hidden quests.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void hiddenFollowUpsAreLeftOutUnlessRevealed() throws Exception {
        String hidden = success(WOOD) + success(SNOWBALLS);

        assertFalse(hidden.contains(STASH), hidden);
        assertFalse(hidden.contains(VAULT), hidden);
        assertFalse(hidden.toLowerCase().contains("hidden"), hidden);

        revealHiddenQuests.set(true);

        assertTrue(success(SNOWBALLS).contains("- `" + STASH + "` Secret Stash (AVAILABLE, hidden)\n"));
        assertTrue(success(WOOD).contains("- `" + VAULT + "` Open the Vault (AVAILABLE, hidden, chapter Developer "
                + "Room)\n"));
    }

    /**
     * Checks hidden prerequisites are left out unless revealed.
     * @throws Exception if the operation fails
     */
    @Test
    void hiddenPrerequisitesAreLeftOutUnlessRevealed() throws Exception {
        revealHiddenQuests.set(true);
        String revealed = success(STASH);

        assertTrue(revealed.contains("## Prerequisites\n\n- `" + SNOWBALLS + "` Make Snowballs (IN_PROGRESS)\n"),
                revealed);

        // A visible quest whose only prerequisite is hidden.
        quests.set(new QuestBook(List.of(chapter(START, "Getting Started", true,
                quest(STASH, START, "Secret Stash").hidden().build(),
                quest(GOLEM, START, "Build a Snow Golem").status(QuestStatus.LOCKED).dependencies(STASH)
                        .build()))));
        revealHiddenQuests.set(false);
        String hidden = success(GOLEM);

        assertTrue(hidden.contains("## Prerequisites\n\nNone.\n"), hidden);
        assertFalse(hidden.contains(STASH), hidden);
    }

    @Test
    void aHiddenQuestIsNotFoundUnlessRevealed() throws Exception {
        for (String id : List.of(STASH, VAULT)) {
            JsonObject result = server.callTool("get_quest", questId(id));
            assertTrue(result.get("isError").getAsBoolean(), result.toString());
            assertEquals(notFound(id), McpTestServer.text(result));
        }

        revealHiddenQuests.set(true);
        String stash = success(STASH);
        String vault = success(VAULT);

        assertTrue(stash.startsWith("# Secret Stash (`" + STASH + "`) (hidden)\n"), stash);
        assertTrue(stash.contains("revealHiddenQuests is on"), stash);
        assertTrue(stash.contains("You found the stash."), stash);
        // Not hidden by itself, but in a chapter the quest book does not list.
        assertTrue(vault.startsWith("# Open the Vault (`" + VAULT + "`) (hidden)\n"), vault);
        assertTrue(vault.contains("- Chapter: Developer Room (`" + SECRET_CHAPTER + "`) (hidden)\n"), vault);
    }

    /**
     * Checks that an unknown quest points callers to both listing and search.
     *
     * @throws Exception if an endpoint request fails
     */
    @Test
    void anUnknownQuestIsAnErrorPointingToListingAndSearch() throws Exception {
        JsonObject result = server.callTool("get_quest", questId("0123456789ABCDEF"));

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertEquals(notFound("0123456789ABCDEF"), McpTestServer.text(result));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000000b2", "#00000000000000B2", " 00000000000000B2 "})
    void questIdsMatchIgnoringCaseAndALeadingHash(String id) throws Exception {
        assertTrue(success(id).startsWith("# Make Snowballs (`" + SNOWBALLS + "`)"));
    }

    @Test
    void aChapterIdIsNotAQuestId() throws Exception {
        JsonObject result = server.callTool("get_quest", questId(START));

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertEquals(notFound(START), McpTestServer.text(result));
    }

    @Test
    void getQuestRequiresAQuestIdAsAString() throws Exception {
        JsonObject missing = server.callTool("get_quest", new JsonObject());
        JsonObject number = new JsonObject();
        number.addProperty("quest_id", 102L);
        JsonObject numeric = server.callTool("get_quest", number);

        assertTrue(missing.get("isError").getAsBoolean(), missing.toString());
        assertTrue(McpTestServer.text(missing).contains("missing required argument \"quest_id\""), missing.toString());
        assertTrue(numeric.get("isError").getAsBoolean(), numeric.toString());
        assertTrue(McpTestServer.text(numeric).contains("argument \"quest_id\" must be a string"),
                numeric.toString());
    }

    @Test
    void aQuestWithoutDescriptionSaysSo() throws Exception {
        assertTrue(success(WOOD).contains("## Description\n\nNone.\n"));
    }

    @Test
    void detailsHiddenUntilStartableAreLeftOutUnlessRevealed() throws Exception {
        String hidden = success(GOLEM);

        assertTrue(hidden.contains("- Status: LOCKED\n"), hidden);
        assertTrue(hidden.contains("The quest book does not open this quest until it can be started, so its "
                + "description, tasks and rewards are not known yet."), hidden);
        assertFalse(hidden.contains("## Description"), hidden);
        assertFalse(hidden.contains("## Tasks"), hidden);
        assertFalse(hidden.contains("## Rewards"), hidden);
        assertFalse(hidden.contains("snow blocks"), hidden);
        assertFalse(hidden.contains("carved_pumpkin"), hidden);
        assertFalse(hidden.contains("pumpkin_pie"), hidden);
        // What the quest book shows on its map: the dependency lines.
        assertTrue(hidden.contains("## Prerequisites\n\n- `" + SNOWBALLS + "` Make Snowballs (IN_PROGRESS)\n"),
                hidden);

        revealHiddenQuests.set(true);
        String revealed = success(GOLEM);

        assertTrue(revealed.contains("its description, tasks and rewards are shown because revealHiddenQuests is "
                + "on."), revealed);
        assertTrue(revealed.contains("Stack two snow blocks and add a pumpkin."), revealed);
        assertTrue(revealed.contains("(`minecraft:carved_pumpkin`)"), revealed);
        assertTrue(revealed.contains("(`minecraft:pumpkin_pie`)"), revealed);
    }

    @Test
    void textHiddenUntilCompleteIsLeftOutUnlessRevealed() throws Exception {
        String hidden = success(PIE);

        assertTrue(hidden.contains("## Description\n\nThe quest book shows the description only once the quest is "
                + "completed.\n"), hidden);
        assertFalse(hidden.contains("secret recipe"), hidden);
        assertTrue(hidden.contains("- 3x Pumpkin (`ftbquests:item`)"), "tasks are still shown: " + hidden);

        revealHiddenQuests.set(true);
        String revealed = success(PIE);

        assertTrue(revealed.contains("## Description (hidden in the quest book until the quest is completed)\n\n"
                + "The secret recipe: pumpkin, sugar and an egg.\n"), revealed);
    }

    /**
     * Checks sequential tasks and invisible rewards follow the reveal setting.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void tasksAndRewardsTheQuestBookDoesNotListAreLeftOutUnlessRevealed() throws Exception {
        String hidden = success(ORDERED);

        assertTrue(hidden.contains("## Tasks\n\n"
                + "- Stone Pickaxe (`ftbquests:item`): 1 × Stone Pickaxe (`minecraft:stone_pickaxe`). "
                + "Progress: 1/1 (done)\n"
                + "- 5x Iron Ore (`ftbquests:item`): 5 × Iron Ore (`minecraft:iron_ore`). Progress: 2/5\n"
                + "\nTasks must be done in order; the quest book lists the next task once this one is done.\n"),
                hidden);
        assertFalse(hidden.contains("Zombie"), hidden);
        assertTrue(hidden.contains("## Rewards\n\n- Apple (`ftbquests:item`): 1 × Apple (`minecraft:apple`)\n\n"),
                hidden);
        assertFalse(hidden.contains("Diamond"), hidden);

        revealHiddenQuests.set(true);
        String revealed = success(ORDERED);

        assertTrue(revealed.contains("- Kill a Zombie (`ftbquests:kill`). Progress: 0/1 (hidden)\n"), revealed);
        assertTrue(revealed.contains("- Secret Diamond (`ftbquests:item`): 1 × Diamond (`minecraft:diamond`) "
                + "(hidden)\n"), revealed);
    }

    /**
     * Checks uses current displayed quest description and link titles.
     * @throws Exception if the operation fails
     */
    @Test
    void usesCurrentDisplayedQuestDescriptionAndLinkTitles() throws Exception {
        game.set(new GameStatus(true, true, true, "zh_cn"));
        quests.set(new QuestBook(List.of(chapter(START, "入门", true,
                quest(WOOD, START, "收集木头").status(QuestStatus.COMPLETED).build(),
                quest(SNOWBALLS, START, "制作雪球").subtitle("冰冷的手")
                        .description(SNOWBALLS_ZH).dependencies(WOOD).build()))));
        String text = success(SNOWBALLS);
        assertTrue(text.startsWith("# 制作雪球 (`" + SNOWBALLS + "`)\n\n- Subtitle: 冰冷的手\n"), text);
        assertTrue(text.contains("- Chapter: 入门 (`" + START + "`)"), text);
        assertTrue(text.contains("先完成[收集木头](quest:" + WOOD + ")。"), text);
        assertFalse(text.contains("No quest text in"), text);
    }

    /**
     * Checks rejects language overrides for quest details.
     * @throws Exception if the operation fails
     */
    @Test
    void rejectsLanguageOverridesForQuestDetails() throws Exception {
        JsonObject arguments = questId(SNOWBALLS);
        arguments.addProperty("lang", "en_us");
        JsonObject result = server.callTool("get_quest", arguments);
        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertTrue(McpTestServer.text(result).contains("unknown argument \"lang\""));
    }

    /**
     * Checks quest reads report an unavailable world.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void getQuestExplainsThatThePlayerIsNotInAWorld() throws Exception {
        game.set(FakeGameSource.MAIN_MENU);

        JsonObject result = server.callTool("get_quest", questId(SNOWBALLS));

        assertTrue(result.get("isError").getAsBoolean(), result.toString());
        assertTrue(McpTestServer.text(result).contains("not in a world"), result.toString());
    }

    /**
     * Checks rich text keeps line structure and drops empty lines.
     * @throws Exception if the operation fails
     */
    @Test
    void richTextKeepsLineStructureAndDropsEmptyLines() throws Exception {
        RichText description = RichText.builder()
                .lineBreak()
                .text("  ").lineBreak()
                .text("Line one\nLine two  ").lineBreak()
                .text(" ").lineBreak()
                .lineBreak()
                .text("Second ").urlLink("https://example.com/a b", "page [1]").lineBreak()
                .paragraphBreak()
                .paragraphBreak()
                .build();
        quests.set(new QuestBook(List.of(chapter(START, "Getting Started", true,
                quest(WOOD, START, "Gather Wood").description(description).build()))));

        String text = success(WOOD);

        assertTrue(text.contains("## Description\n\nLine one\nLine two\n\nSecond [page \\[1\\]](<https://example.com/a b>)\n\n"
                + "## Tasks"), text);
    }

    /**
     * Calls the endpoint and extracts successful quest details.
     * @param id requested quest ID
     * @return Markdown quest details
     * @throws Exception if the endpoint request fails
     */
    private String success(String id) throws Exception {
        JsonObject arguments = questId(id);
        JsonObject result = server.callTool("get_quest", arguments);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }

    /**
     * Builds the expected unknown-quest guidance.
     *
     * @param id the requested quest ID
     * @return the error text shown to callers
     */
    private static String notFound(String id) {
        return "No quest with ID `" + id + "` exists. Call get_chapter to list the quests of a chapter with their IDs, "
                + "or search_quests to find a quest.";
    }

    private static JsonObject questId(String id) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("quest_id", id);
        return arguments;
    }
}
