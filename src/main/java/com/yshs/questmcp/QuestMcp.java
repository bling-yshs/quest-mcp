package com.yshs.questmcp;

import com.mojang.logging.LogUtils;
import com.yshs.questmcp.client.McpServerLifecycle;
import com.yshs.questmcp.client.McpStatusChat;
import com.yshs.questmcp.client.QuestMcpConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.slf4j.Logger;

@Mod(value = QuestMcp.MOD_ID, dist = Dist.CLIENT)
public class QuestMcp {
    public static final String MOD_ID = "questmcp";
    private static final Logger LOGGER = LogUtils.getLogger();

    public QuestMcp(IEventBus modBus, ModContainer container) {
        String version = container.getModInfo().getVersion().toString();
        LOGGER.info("Quest MCP {} loaded", version);

        container.registerConfig(ModConfig.Type.CLIENT, QuestMcpConfig.SPEC);

        McpServerLifecycle server = new McpServerLifecycle(version);
        modBus.addListener(FMLClientSetupEvent.class, event -> server.start());
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, event -> server.stop());
        new McpStatusChat(server).register(NeoForge.EVENT_BUS);
    }
}
