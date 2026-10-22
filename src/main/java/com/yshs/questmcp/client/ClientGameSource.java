package com.yshs.questmcp.client;

import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.GameStatus;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.minecraft.client.Minecraft;

/** Reads the game status from the Minecraft client, FTB Quests and JEI. Must run on the client main thread. */
final class ClientGameSource implements GameSource {
    @Override
    public GameStatus status() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null && minecraft.player != null;
        return new GameStatus(
                inWorld,
                // The quest book opens only once the team's progress has arrived too (ClientQuestFile.openQuestGui).
                inWorld && ClientQuestFile.exists() && ClientQuestFile.getInstance().selfTeamData.isValid(),
                inWorld && QuestMcpJeiPlugin.runtime() != null,
                minecraft.getLanguageManager().getSelected());
    }
}
