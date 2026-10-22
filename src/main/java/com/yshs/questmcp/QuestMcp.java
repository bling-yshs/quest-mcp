package com.yshs.questmcp;

import com.mojang.logging.LogUtils;
import com.yshs.questmcp.client.McpServerLifecycle;
import com.yshs.questmcp.client.McpStatusChat;
import com.yshs.questmcp.client.QuestMcpConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(QuestMcp.MOD_ID)
public class QuestMcp {
    public static final String MOD_ID = "questmcp";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Initializes the Forge mod and registers its endpoint only on the physical client.
     * @param context Forge-provided mod loading context
     */
    public QuestMcp(FMLJavaModLoadingContext context) {
        String version = context.getContainer().getModInfo().getVersion().toString();
        LOGGER.info("Quest MCP {} loaded", version);

        var modBus = context.getModEventBus();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            context.registerConfig(ModConfig.Type.CLIENT, QuestMcpConfig.SPEC);
            McpServerLifecycle server = new McpServerLifecycle(version);
            modBus.addListener((FMLClientSetupEvent event) -> server.start());
            MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> server.stop());
            new McpStatusChat(server).register(MinecraftForge.EVENT_BUS);
        });
    }
}
