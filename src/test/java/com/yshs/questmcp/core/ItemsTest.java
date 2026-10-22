package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.RecipeSource.Ingredient;
import com.yshs.questmcp.core.source.RecipeSource.IngredientInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/** JEI ingredient behavior exercised through the real MCP endpoint. */
class ItemsTest {
    /**
     * Creates a source with localized, ambiguous and fluid identities.
     * @return fake ingredient data
     */
    private static FakeRecipeSource source() {
        FakeRecipeSource source = new FakeRecipeSource();
        source.entries = List.of(
                new IngredientInfo(new Ingredient("minecraft:snowball", IngredientKind.ITEM, "雪球", "minecraft"),
                        List.of("Cold projectile"), List.of("c:snowballs"), List.of("Dig snow with a shovel.")),
                new IngredientInfo(new Ingredient("mod:snowball", IngredientKind.ITEM, "雪球", "mod"),
                        List.of(), List.of(), List.of()),
                new IngredientInfo(new Ingredient("minecraft:water", IngredientKind.FLUID, "水", "minecraft"),
                        List.of("Fluid"), List.of("c:water"), List.of()));
        return source;
    }

    /**
     * Checks current display names and case-insensitive registry IDs retain identities and kind.
     * @throws Exception if the endpoint fails
     */
    @Test
    void searchesCurrentDisplayNamesIdsAndKinds() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, false, true, "zh_cn"));
        try (McpTestServer server = new McpTestServer(game, source())) {
            for (String query : List.of("雪球", "SNOWBALL")) {
                JsonObject args = args("query", query);
                String text = success(server, "search_items", args);
                assertTrue(text.startsWith("2 total, showing 1–2"), text);
                assertTrue(text.contains("`minecraft:snowball` (item) 雪球"), text);
                assertTrue(text.contains("`mod:snowball` (item) 雪球 — Mod: `mod`"), text);
            }
            assertTrue(success(server, "search_items", args("query", "minecraft:snowball")).startsWith("1 total"));
            assertTrue(success(server, "search_items", args("query", "水")).contains("(fluid) 水"));
            assertTrue(success(server, "search_items", args("query", "Snow Ball")).startsWith("0 total"));
            for (String tool : List.of("search_items", "get_item_info")) {
                JsonObject input = args(tool.equals("search_items") ? "query" : "id", "minecraft:snowball");
                input.addProperty("lang", "en_us");
                JsonObject result = server.callTool(tool, input);
                assertTrue(result.get("isError").getAsBoolean(), result.toString());
                assertTrue(McpTestServer.text(result).contains("unknown argument \"lang\""));
            }
        }
    }

    /**
     * Checks readable tooltip, tags, information pages, default item kind and fluid details.
     * @throws Exception if the endpoint fails
     */
    @Test
    void readsDetailsAndDistinguishesFluids() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, source())) {
            String text = success(server, "get_item_info", args("id", "minecraft:snowball"));
            assertTrue(text.contains("# 雪球"), text);
            assertTrue(text.contains("Cold projectile"), text);
            assertTrue(text.contains("`#c:snowballs`"), text);
            assertTrue(text.contains("Dig snow with a shovel."), text);
            JsonObject fluid = args("id", "minecraft:water");
            assertTrue(server.callTool("get_item_info", fluid).get("isError").getAsBoolean());
            fluid.addProperty("kind", "fluid");
            assertTrue(success(server, "get_item_info", fluid).contains("- Kind: fluid"));
            String empty = success(server, "get_item_info", args("id", "mod:snowball"));
            assertTrue(empty.contains("## JEI information\n\nNone."), empty);
            assertTrue(server.callTool("get_item_info", args("id", "missing:item")).get("isError").getAsBoolean());
        }
    }

    /**
     * Checks paging, empty results, invalid arguments and literal matching.
     * @throws Exception if the endpoint fails
     */
    @Test
    void pagesAndValidatesSearchArguments() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, source())) {
            JsonObject args = args("query", "snowball");
            args.addProperty("limit", 1);
            args.addProperty("offset", 1);
            assertTrue(success(server, "search_items", args).startsWith("2 total, showing 2–2"));
            args.addProperty("offset", Integer.MAX_VALUE);
            assertTrue(success(server, "search_items", args).startsWith("2 total, showing 0–0"));
            for (int bound : List.of(0, 51, -1)) {
                args.addProperty("limit", bound);
                assertTrue(server.callTool("search_items", args).get("isError").getAsBoolean());
            }
            assertTrue(success(server, "search_items", args("query", ".*")).startsWith("0 total"));
            assertTrue(server.callTool("search_items", args("query", "   ")).get("isError").getAsBoolean());
            assertTrue(server.callTool("search_items", new JsonObject()).get("isError").getAsBoolean());
            JsonObject invalidKind = args("id", "minecraft:water");
            invalidKind.addProperty("kind", "gas");
            assertTrue(server.callTool("get_item_info", invalidKind).get("isError").getAsBoolean());
        }
    }

    /**
     * Checks main-menu and JEI-not-ready failures for both tools.
     * @throws Exception if the endpoint fails
     */
    @Test
    void explainsUnavailableGameAndJeiState() throws Exception {
        FakeGameSource game = new FakeGameSource();
        try (McpTestServer server = new McpTestServer(game, source())) {
            for (String tool : List.of("search_items", "get_item_info")) {
                JsonObject args = args(tool.equals("search_items") ? "query" : "id", "minecraft:snowball");
                game.set(FakeGameSource.MAIN_MENU);
                JsonObject menu = server.callTool(tool, args);
                assertTrue(menu.get("isError").getAsBoolean());
                assertTrue(McpTestServer.text(menu).contains("not in a world"));
                game.set(new GameStatus(true, true, false, "en_us"));
                JsonObject loading = server.callTool(tool, args);
                assertTrue(loading.get("isError").getAsBoolean());
                assertTrue(McpTestServer.text(loading).contains("JEI_NOT_READY"));
            }
        }
    }

    /**
     * Builds one string argument.
     * @param key argument name
     * @param value argument value
     * @return tool arguments
     */
    private static JsonObject args(String key, String value) {
        JsonObject args = new JsonObject();
        args.addProperty(key, value);
        return args;
    }

    /**
     * Extracts a successful text result.
     * @param server endpoint
     * @param tool tool name
     * @param args tool arguments
     * @return Markdown result
     * @throws Exception if the endpoint fails
     */
    private static String success(McpTestServer server, String tool, JsonObject args) throws Exception {
        JsonObject result = server.callTool(tool, args);
        assertFalse(result.get("isError").getAsBoolean(), result.toString());
        return McpTestServer.text(result);
    }
}
