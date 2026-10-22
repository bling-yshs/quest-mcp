package com.yshs.questmcp.core;

import com.yshs.questmcp.core.mcp.McpDispatcher;
import com.yshs.questmcp.core.mcp.McpHttpServer;
import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.RecipeSource;
import com.yshs.questmcp.core.tool.RecipeReader;
import com.yshs.questmcp.core.tool.SearchItemsTool;
import com.yshs.questmcp.core.tool.GetItemInfoTool;
import com.yshs.questmcp.core.tool.GetRecipesTool;
import com.yshs.questmcp.core.tool.ListCategoriesTool;
import com.yshs.questmcp.core.tool.GetCategoryRecipesTool;
import com.yshs.questmcp.core.tool.GameThread;
import com.yshs.questmcp.core.tool.GetChapterTool;
import com.yshs.questmcp.core.tool.GetCurrentQuestsTool;
import com.yshs.questmcp.core.tool.GetViewedQuestTool;
import com.yshs.questmcp.core.tool.GetQuestTool;
import com.yshs.questmcp.core.tool.GetStatusTool;
import com.yshs.questmcp.core.tool.ListChaptersTool;
import com.yshs.questmcp.core.tool.QuestBookReader;
import com.yshs.questmcp.core.tool.SearchQuestsTool;
import com.yshs.questmcp.core.tool.ToolRegistry;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * The Quest MCP server: the HTTP endpoint wired to the dispatcher, the tools and the game behind its source interfaces.
 * Free of Minecraft types, so the mod and the endpoint tests start the very same server.
 */
public final class QuestMcpServer {
    public static final String NAME = "questmcp";
    /** How long a tool waits for the game thread before answering with an error. */
    public static final Duration DEFAULT_GAME_TIMEOUT = Duration.ofSeconds(5);

    private final McpHttpServer http;

    private QuestMcpServer(McpHttpServer http) {
        this.http = http;
    }

    /**
     * Starts the MCP endpoint with the quest and status tools.
     *
     * @param port               the port to bind on 127.0.0.1, or {@code 0} for an ephemeral one
     * @param token              the bearer token clients must send, or blank to accept local clients without one
     * @param modVersion         reported to clients as the server version
     * @param game               reads the live game state; only called on the game thread
     * @param quests             reads the quest book; only called on the game thread
     * @param recipes            reads JEI; only called on the game thread
     * @param revealHiddenQuests whether quest tools include what the player cannot see in the quest book; read on
     *                           every call, so a changed setting applies at once
     * @param gameExecutor       runs tasks on the game thread
     * @param gameTimeout        how long a tool waits for the game thread
     * @return the running server
     * @throws IOException if the port cannot be bound; see {@link ServerStatus.Failed#of}
     */
    public static QuestMcpServer start(int port, String token, String modVersion, GameSource game,
            QuestSource quests, RecipeSource recipes, BooleanSupplier revealHiddenQuests, Executor gameExecutor, Duration gameTimeout)
            throws IOException {
        GameThread gameThread = new GameThread(gameExecutor, gameTimeout);
        QuestBookReader questBook = new QuestBookReader(game, quests, revealHiddenQuests, gameThread);
        RecipeReader recipeReader = new RecipeReader(game, recipes, gameThread);
        ToolRegistry tools = new ToolRegistry(List.of(
                new GetStatusTool(modVersion, game, revealHiddenQuests, gameThread),
                new ListChaptersTool(questBook),
                new GetChapterTool(questBook),
                new GetQuestTool(questBook),
                new SearchQuestsTool(questBook),
                new GetCurrentQuestsTool(questBook),
                new GetViewedQuestTool(questBook),
                new SearchItemsTool(recipeReader),
                new GetItemInfoTool(recipeReader),
                new GetRecipesTool(recipeReader, false),
                new GetRecipesTool(recipeReader, true),
                new ListCategoriesTool(recipeReader),
                new GetCategoryRecipesTool(recipeReader)));
        return new QuestMcpServer(McpHttpServer.start(port, token, new McpDispatcher(NAME, modVersion, tools)));
    }

    /** The endpoint URL clients connect to, e.g. {@code http://127.0.0.1:25599/mcp}. */
    public URI url() {
        return http.url();
    }

    public void stop() {
        http.stop();
    }
}
