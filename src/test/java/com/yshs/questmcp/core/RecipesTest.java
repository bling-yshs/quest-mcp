package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.RecipeSource.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Endpoint checks for generic JEI recipe rendering and filtering. */
class RecipesTest {
    static final Ingredient BALL = new Ingredient("minecraft:snowball", IngredientKind.ITEM, "Snowball", "minecraft");
    static final Ingredient IRON = new Ingredient("minecraft:iron_ingot", IngredientKind.ITEM, "Iron Ingot", "minecraft");
    static final Ingredient WATER = new Ingredient("minecraft:water", IngredientKind.FLUID, "Water", "minecraft");
    static final Ingredient MACHINE = new Ingredient("mod:machine", IngredientKind.ITEM, "Machine", "mod");

    /**
     * Creates neutral shaped, shapeless, fluid and machine recipes.
     * @return the fake JEI source
     */
    static FakeRecipeSource source() {
        FakeRecipeSource source = new FakeRecipeSource();
        source.entries = List.of(BALL, IRON, WATER, MACHINE).stream()
                .map(item -> new IngredientInfo(item, List.of(), List.of(), List.of())).toList();
        List<Amount> logs = List.of("oak", "birch", "spruce", "acacia").stream()
                .map(name -> new Amount(new Ingredient("minecraft:" + name + "_log", IngredientKind.ITEM, name + " log", "minecraft"), 1)).toList();
        source.recipeEntries = List.of(
                new Recipe("minecraft:crafting", "Crafting", List.of(
                        new Slot(Role.INPUT, logs, Optional.of("minecraft:logs"), Optional.of(new GridPosition(0, 0))),
                        new Slot(Role.INPUT, List.of(new Amount(IRON, 1)), Optional.empty(), Optional.of(new GridPosition(1, 2))),
                        slot(Role.OUTPUT, BALL, 2))),
                new Recipe("minecraft:crafting", "Crafting", List.of(slot(Role.INPUT, BALL, 4), slot(Role.OUTPUT, IRON, 1))),
                new Recipe("mod:machine", "Machine processing", List.of(
                        slot(Role.INPUT, WATER, 1000), slot(Role.INPUT, IRON, 2), slot(Role.OUTPUT, BALL, 3),
                        slot(Role.CATALYST, MACHINE, 1))),
                new Recipe("minecraft:smelting", "Smelting", List.of(slot(Role.INPUT, IRON, 1), slot(Role.OUTPUT, IRON, 1))));
        return source;
    }

    /**
     * Creates a single-alternative slot.
     * @param role slot role
     * @param item ingredient
     * @param amount count or millibuckets
     * @return neutral recipe slot
     */
    private static Slot slot(Role role, Ingredient item, long amount) {
        return new Slot(role, List.of(new Amount(item, amount)), Optional.empty(), Optional.empty());
    }

    /**
     * Checks output recipes, complete category counts, tags, grid positions and fluids/catalysts.
     * @throws Exception if the endpoint fails
     */
    @Test
    void rendersShapedTagsFluidAndMachineRecipes() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, source())) {
            String result = success(server, "get_recipes", args("minecraft:snowball"));
            assertTrue(result.startsWith("2 total, showing 1–2"), result);
            assertTrue(result.contains("Crafting (`minecraft:crafting`): 1"), result);
            assertTrue(result.contains("Machine processing (`mod:machine`): 1"), result);
            assertTrue(result.contains("tag #minecraft:logs (e.g. 1 × oak log"), result);
            assertFalse(result.contains("acacia"), result);
            assertTrue(result.contains("| `#minecraft:logs` |  |  |"), result);
            assertTrue(result.contains("|  |  | `minecraft:iron_ingot` |"), result);
            assertTrue(result.contains("1000 mB × Water (`minecraft:water`)"), result);
            assertTrue(result.contains("Catalysts:\n- 1 × Machine (`mod:machine`)"), result);
            assertTrue(result.contains("3 × Snowball (`minecraft:snowball`)"), result);
        }
    }

    /**
     * Checks consuming direction, shapeless output and category filtering.
     * @throws Exception if the endpoint fails
     */
    @Test
    void findsUsesAndFiltersCategories() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, source())) {
            String uses = success(server, "get_uses", args("minecraft:iron_ingot"));
            assertTrue(uses.startsWith("3 total"), uses);
            JsonObject filter = args("minecraft:iron_ingot");
            filter.addProperty("category", "minecraft:crafting");
            String crafting = success(server, "get_recipes", filter);
            assertTrue(crafting.startsWith("1 total"), crafting);
            assertTrue(crafting.contains("4 × Snowball"), crafting);
            assertFalse(crafting.contains("Crafting grid"), crafting);
            assertFalse(crafting.contains("mod:machine"), crafting);
            JsonObject fluid = args("minecraft:water");
            fluid.addProperty("kind", "fluid");
            assertTrue(success(server, "get_uses", fluid).startsWith("1 total"));
            assertTrue(success(server, "get_recipes", fluid).startsWith("0 total"));
        }
    }

    /**
     * Checks default paging, the maximum page, last page and beyond-end offsets.
     * @throws Exception if the endpoint fails
     */
    @Test
    void pagesWithFullCountsAndRejectsInvalidBounds() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        FakeRecipeSource source = source();
        source.recipeEntries = java.util.Collections.nCopies(55, source.recipeEntries.get(0));
        try (McpTestServer server = new McpTestServer(game, source)) {
            assertTrue(success(server, "get_recipes", args("minecraft:snowball")).startsWith("55 total, showing 1–10"));
            JsonObject args = args("minecraft:snowball");
            args.addProperty("limit", 50);
            assertTrue(success(server, "get_recipes", args).startsWith("55 total, showing 1–50"));
            args.addProperty("offset", 50);
            assertTrue(success(server, "get_recipes", args).startsWith("55 total, showing 51–55"));
            args.addProperty("offset", Integer.MAX_VALUE);
            assertTrue(success(server, "get_recipes", args).startsWith("55 total, showing 0–0"));
            for (int limit : List.of(0, 51)) {
                args.addProperty("limit", limit);
                assertTrue(server.callTool("get_recipes", args).get("isError").getAsBoolean());
            }
            args.addProperty("limit", 1);
            args.addProperty("offset", -1);
            assertTrue(server.callTool("get_uses", args).get("isError").getAsBoolean());
        }
    }

    /**
     * Checks alternatives stay a choice and unknown amounts are not fabricated.
     * @throws Exception if the endpoint fails
     */
    @Test
    void preservesAlternativeAmountsAndUnspecifiedCounts() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        FakeRecipeSource source = source();
        source.recipeEntries = List.of(new Recipe("mod:choice", "Choice", List.of(
                new Slot(Role.INPUT, List.of(new Amount(BALL, 2), new Amount(IRON, 3)),
                        Optional.empty(), Optional.empty()),
                slot(Role.OUTPUT, BALL, -1))));
        try (McpTestServer server = new McpTestServer(game, source)) {
            String result = success(server, "get_recipes", args("minecraft:snowball"));
            assertTrue(result.contains("any of 2 × Snowball"), result);
            assertTrue(result.contains("3 × Iron Ingot"), result);
            assertTrue(result.contains("unspecified × Snowball"), result);
        }
    }

    /**
     * Checks unknown IDs/categories, missing arguments and readiness errors for both directions.
     * @throws Exception if the endpoint fails
     */
    @Test
    void reportsUnknownIdentitiesAndUnavailableJei() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, source())) {
            for (String tool : List.of("get_recipes", "get_uses")) {
                assertTrue(server.callTool(tool, args("unknown:item")).get("isError").getAsBoolean());
                JsonObject category = args("minecraft:snowball");
                category.addProperty("category", "unknown:category");
                assertTrue(server.callTool(tool, category).get("isError").getAsBoolean());
                assertTrue(server.callTool(tool, new JsonObject()).get("isError").getAsBoolean());
                game.set(new GameStatus(true, true, false, "en_us"));
                assertTrue(McpTestServer.text(server.callTool(tool, args("minecraft:snowball"))).contains("JEI_NOT_READY"));
                game.set(FakeGameSource.MAIN_MENU);
                assertTrue(McpTestServer.text(server.callTool(tool, args("minecraft:snowball"))).contains("not in a world"));
                game.set(new GameStatus(true, true, true, "en_us"));
            }
        }
    }

    /**
     * Creates a registry-ID argument.
     * @param id registry ID
     * @return tool arguments
     */
    static JsonObject args(String id) {
        JsonObject args = new JsonObject();
        args.addProperty("id", id);
        return args;
    }

    /**
     * Calls an endpoint tool expecting a successful text result.
     * @param server test server
     * @param tool MCP tool name
     * @param args tool arguments
     * @return Markdown text
     * @throws Exception if the endpoint fails
     */
    static String success(McpTestServer server, String tool, JsonObject args) throws Exception {
        JsonObject result = server.callTool(tool, args);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }
}
