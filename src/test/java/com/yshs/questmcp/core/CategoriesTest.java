package com.yshs.questmcp.core;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.RecipeSource.Recipe;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Endpoint checks for JEI category browsing and shared recipe output. */
class CategoriesTest {
    /**
     * Checks category identities, counts, crafting stations, listing order and paging.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void listsCountsCatalystsAndPages() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, RecipesTest.source())) {
            String result = RecipesTest.success(server, "list_categories", new JsonObject());
            assertTrue(result.startsWith("3 total, showing 1–3"), result);
            assertTrue(result.contains("`minecraft:crafting` Crafting — 2 recipes"), result);
            assertTrue(result.contains("`mod:machine` Machine processing — 1 recipe"), result);
            assertTrue(result.contains("1 × Machine (`mod:machine`)"), result);
            JsonObject args = new JsonObject();
            args.addProperty("limit", 1);
            args.addProperty("offset", 1);
            String page = RecipesTest.success(server, "list_categories", args);
            assertTrue(page.startsWith("3 total, showing 2–2"), page);
            assertTrue(page.contains("mod:machine"), page);
            assertFalse(page.contains("minecraft:crafting"), page);
            args.addProperty("offset", Integer.MAX_VALUE);
            assertTrue(RecipesTest.success(server, "list_categories", args).startsWith("3 total, showing 0–0"));
        }
    }

    /**
     * Checks category recipes use exactly the ingredient-lookup renderer.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void categoryRecipeRenderingMatchesIngredientLookup() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, RecipesTest.source())) {
            JsonObject recipeArgs = RecipesTest.args("minecraft:snowball");
            recipeArgs.addProperty("category", "mod:machine");
            JsonObject categoryArgs = new JsonObject();
            categoryArgs.addProperty("category", "mod:machine");
            assertEquals(RecipesTest.success(server, "get_recipes", recipeArgs),
                    RecipesTest.success(server, "get_category_recipes", categoryArgs));
            categoryArgs.addProperty("category", "minecraft:crafting");
            categoryArgs.addProperty("limit", 1);
            categoryArgs.addProperty("offset", 1);
            String page = RecipesTest.success(server, "get_category_recipes", categoryArgs);
            assertTrue(page.startsWith("2 total, showing 2–2"), page);
            assertTrue(page.contains("4 × Snowball"), page);
            assertFalse(page.contains("Crafting grid"), page);
            categoryArgs.addProperty("offset", 2);
            assertTrue(RecipesTest.success(server, "get_category_recipes", categoryArgs).startsWith("2 total, showing 0–0"));
        }
    }

    /**
     * Checks default and maximum category page sizes plus empty category data.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void handlesLargeAndEmptyCategoryLists() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        FakeRecipeSource source = RecipesTest.source();
        Recipe template = source.recipeEntries.getFirst();
        source.recipeEntries = java.util.stream.IntStream.range(0, 55)
                .mapToObj(i -> new Recipe("mod:category_" + i, "Category " + i, template.slots())).toList();
        try (McpTestServer server = new McpTestServer(game, source)) {
            assertTrue(RecipesTest.success(server, "list_categories", new JsonObject()).startsWith("55 total, showing 1–10"));
            JsonObject args = new JsonObject();
            args.addProperty("limit", 50);
            assertTrue(RecipesTest.success(server, "list_categories", args).startsWith("55 total, showing 1–50"));
            args.addProperty("offset", 50);
            assertTrue(RecipesTest.success(server, "list_categories", args).startsWith("55 total, showing 51–55"));
            source.recipeEntries = List.of();
            assertEquals("0 total, showing 0–0\n\nNo recipe categories.\n",
                    RecipesTest.success(server, "list_categories", new JsonObject()));
        }
    }

    /**
     * Checks unknown categories, required arguments, paging errors, game states and current tool discovery.
     * @throws Exception if an endpoint request fails
     */
    @Test
    void reportsCategoryErrorsAndAdvertisesAllThirteenTools() throws Exception {
        FakeGameSource game = new FakeGameSource();
        game.set(new GameStatus(true, true, true, "en_us"));
        try (McpTestServer server = new McpTestServer(game, RecipesTest.source())) {
            JsonObject args = new JsonObject();
            args.addProperty("category", "unknown:category");
            JsonObject missing = server.callTool("get_category_recipes", args);
            assertTrue(missing.get("isError").getAsBoolean());
            assertTrue(McpTestServer.text(missing).contains("list_categories"));
            assertTrue(server.callTool("get_category_recipes", new JsonObject()).get("isError").getAsBoolean());
            args.addProperty("category", "minecraft:crafting");
            for (String tool : List.of("list_categories", "get_category_recipes")) {
                JsonObject input = tool.equals("list_categories") ? new JsonObject() : args.deepCopy();
                for (int limit : List.of(0, 51)) {
                    input.addProperty("limit", limit);
                    assertTrue(server.callTool(tool, input).get("isError").getAsBoolean());
                }
                input.addProperty("limit", 1);
                input.addProperty("offset", -1);
                assertTrue(server.callTool(tool, input).get("isError").getAsBoolean());
                input.addProperty("offset", 0);
                game.set(FakeGameSource.MAIN_MENU);
                assertTrue(McpTestServer.text(server.callTool(tool, input)).contains("not in a world"));
                game.set(new GameStatus(true, true, false, "en_us"));
                assertTrue(McpTestServer.text(server.callTool(tool, input)).contains("JEI_NOT_READY"));
                game.set(new GameStatus(true, true, true, "en_us"));
            }
            var tools = server.request("tools/list", new JsonObject()).getAsJsonObject("result").getAsJsonArray("tools");
            assertEquals(13, tools.size());
            java.util.Set<String> names = new java.util.HashSet<>();
            tools.forEach(tool -> names.add(tool.getAsJsonObject().get("name").getAsString()));
            assertTrue(names.containsAll(List.of("list_categories", "get_category_recipes", "get_recipes", "get_uses")));
        }
    }
}
