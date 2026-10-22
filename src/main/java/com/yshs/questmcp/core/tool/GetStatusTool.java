package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.GameState;
import com.yshs.questmcp.core.source.GameStatus;
import java.util.function.BooleanSupplier;

/** {@code get_status}: whether the game can currently answer quest and recipe questions, and why not. */
public final class GetStatusTool implements Tool {
    private final String modVersion;
    private final GameSource game;
    private final BooleanSupplier revealHiddenQuests;
    private final GameThread gameThread;

    public GetStatusTool(String modVersion, GameSource game, BooleanSupplier revealHiddenQuests,
            GameThread gameThread) {
        this.modVersion = modVersion;
        this.game = game;
        this.revealHiddenQuests = revealHiddenQuests;
        this.gameThread = gameThread;
    }

    @Override
    public String name() {
        return "get_status";
    }

    @Override
    public String description() {
        return "Report the state of the player's running Minecraft game: Quest MCP version, whether the player is in "
                + "a world, whether the FTB Quests quest book and JEI recipes are loaded, the game language, and whether "
                + "hidden quests are revealed. Call this when other tools say the game is not ready, to explain why.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    @Override
    public String call(JsonObject arguments) throws ToolException {
        GameStatus status = gameThread.read(game::status);
        GameState state = status.state();
        boolean reveal = revealHiddenQuests.getAsBoolean();
        return "# Quest MCP status\n\n"
                + "- Mod version: `" + modVersion + "`\n"
                + "- Game state: `" + state + "` (" + explain(state) + ")\n"
                + "- Language: `" + status.language() + "`\n"
                + "- Quest book: " + (status.questsLoaded() ? "loaded" : "not loaded") + "\n"
                + "- JEI: " + (status.jeiReady() ? "ready" : "not ready") + "\n"
                + "- revealHiddenQuests: `" + reveal + "` ("
                + (reveal
                        ? "quest tools include quests the player cannot see in the quest book, marked hidden"
                        : "quest tools only show what the player can see in the quest book") + ")\n";
    }

    private static String explain(GameState state) {
        return switch (state) {
            case NOT_IN_WORLD -> "the player is not in a world; quest and recipe tools work once a world is joined";
            case QUESTS_NOT_LOADED -> "in a world, but the quest book has not been received from the server yet";
            case JEI_NOT_READY -> "in a world, but JEI has not finished loading its recipes yet";
            case READY -> "in a world; quests and recipes are available";
        };
    }
}
