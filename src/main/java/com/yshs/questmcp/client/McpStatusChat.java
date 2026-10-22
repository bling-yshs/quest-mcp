package com.yshs.questmcp.client;

import com.mojang.brigadier.Command;
import com.yshs.questmcp.core.ServerStatus;
import com.yshs.questmcp.core.source.GameState;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Tells the player where to connect their AI client: a chat message on every world join, and the {@code /questmcp}
 * client command for status and restarting the endpoint on demand. Clicking the displayed URL copies it.
 */
public final class McpStatusChat {
    private static final String RETRY_KEY = "questmcp.server.retry";

    private final McpServerLifecycle server;
    private final ClientGameSource game = new ClientGameSource();

    public McpStatusChat(McpServerLifecycle server) {
        this.server = server;
    }

    /** Registers the command and the world-join message on NeoForge's game event bus. */
    public void register(IEventBus gameBus) {
        gameBus.addListener(RegisterClientCommandsEvent.class, this::registerCommand);
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> showOnWorldJoin());
    }

    /**
     * Registers status and same-port restart commands on the client dispatcher.
     * @param event client command registration event
     */
    private void registerCommand(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("questmcp").executes(context -> {
            for (Component line : statusLines(server.status(), game.status().state())) {
                context.getSource().sendSuccess(() -> line, false);
            }
            return Command.SINGLE_SUCCESS;
        }).then(Commands.literal("restart").executes(context -> {
            server.restart();
            ServerStatus status = server.status();
            if (status instanceof ServerStatus.Running running) {
                context.getSource().sendSuccess(() -> Component.translatable("questmcp.command.restart.running",
                        copyableUrl(running)), false);
                return Command.SINGLE_SUCCESS;
            }
            context.getSource().sendFailure(worldJoinMessage(status));
            return 0;
        })));
    }

    /**
     * Retries a stopped or failed endpoint once on world join, then displays the resulting status.
     * Running endpoints keep serving through the lifecycle's existing start guard.
     */
    private void showOnWorldJoin() {
        server.start();
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(worldJoinMessage(server.status()));
    }

    /**
     * Builds the join or restart-failure message with recovery guidance.
     * @param status current endpoint status
     * @return localized message with a copyable URL or retry command
     */
    private static Component worldJoinMessage(ServerStatus status) {
        return switch (status) {
            case ServerStatus.Running running ->
                    Component.translatable("questmcp.join.running", copyableUrl(running));
            case ServerStatus.Failed failed ->
                    Component.translatable("questmcp.join.failed", failure(failed),
                            Component.translatable(RETRY_KEY)).withStyle(ChatFormatting.RED);
            case ServerStatus.Stopped stopped ->
                    Component.translatable("questmcp.join.stopped").withStyle(ChatFormatting.RED);
        };
    }

    /**
     * Builds the endpoint and game status shown by the root command.
     * @param status current endpoint status
     * @param state current game readiness
     * @return localized status lines
     */
    private static List<Component> statusLines(ServerStatus status, GameState state) {
        Component server = switch (status) {
            case ServerStatus.Running running ->
                    Component.translatable("questmcp.command.server.running", copyableUrl(running));
            case ServerStatus.Failed failed ->
                    Component.translatable("questmcp.command.server.failed", failure(failed),
                            Component.translatable(RETRY_KEY)).withStyle(ChatFormatting.RED);
            case ServerStatus.Stopped stopped ->
                    Component.translatable("questmcp.command.server.stopped").withStyle(ChatFormatting.RED);
        };
        return List.of(
                Component.translatable("questmcp.command.title").withStyle(ChatFormatting.GOLD),
                server,
                Component.translatable("questmcp.command.game", Component.translatable(gameStateKey(state))));
    }

    /** The URL in green brackets; clicking copies it, shift-clicking inserts it into the chat box. */
    private static Component copyableUrl(ServerStatus.Running running) {
        return ComponentUtils.copyOnClickText(running.url().toString());
    }

    private static Component failure(ServerStatus.Failed failed) {
        // The port is passed as text so that it is never formatted with digit grouping.
        return Component.translatable("questmcp.server.failed", String.valueOf(failed.port()), failed.detail());
    }

    private static String gameStateKey(GameState state) {
        return switch (state) {
            case NOT_IN_WORLD -> "questmcp.game_state.not_in_world";
            case QUESTS_NOT_LOADED -> "questmcp.game_state.quests_not_loaded";
            case JEI_NOT_READY -> "questmcp.game_state.jei_not_ready";
            case READY -> "questmcp.game_state.ready";
        };
    }
}
