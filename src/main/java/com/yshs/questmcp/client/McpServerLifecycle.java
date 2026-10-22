package com.yshs.questmcp.client;

import com.mojang.logging.LogUtils;
import com.yshs.questmcp.core.QuestMcpServer;
import com.yshs.questmcp.core.ServerStatus;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Runs the MCP server across world changes, with retry on world join and an explicit same-port restart.
 */
public final class McpServerLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String modVersion;
    private @Nullable QuestMcpServer server;
    private ServerStatus status = new ServerStatus.Stopped();

    public McpServerLifecycle(String modVersion) {
        this.modVersion = modVersion;
    }

    /**
     * Starts the HTTP endpoint on the configured port when it is not already running.
     * Failed starts can be retried by the world-join handler or restart command.
     */
    public synchronized void start() {
        if (server != null) {
            return;
        }
        int port = QuestMcpConfig.PORT.get();
        String token = QuestMcpConfig.TOKEN.get();
        try {
            server = QuestMcpServer.start(port, token, modVersion, new ClientGameSource(), new FtbQuestSource(),
                    new JeiRecipeSource(),
                    QuestMcpConfig.REVEAL_HIDDEN_QUESTS::get, task -> Minecraft.getInstance().execute(task),
                    QuestMcpServer.DEFAULT_GAME_TIMEOUT);
            status = new ServerStatus.Running(server.url());
            LOGGER.info("Quest MCP server listening on {}{}", server.url(),
                    token.isBlank() ? "" : " (bearer token required)");
        } catch (IOException e) {
            ServerStatus.Failed failed = ServerStatus.Failed.of(port, e);
            status = failed;
            LOGGER.error("Quest MCP server did not start: {}. Make the configured port available, then run "
                    + "\"/questmcp restart\" to retry on the same port.", failed.reason());
        }
    }

    /**
     * Stops this instance's endpoint and starts it again using the configured port and token.
     * A previous bind failure is retried without changing the configured port.
     */
    public synchronized void restart() {
        stop();
        start();
    }

    /** Whether the server is running, and if it failed to start, why. */
    public synchronized ServerStatus status() {
        return status;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop();
            server = null;
            status = new ServerStatus.Stopped();
        }
    }
}
