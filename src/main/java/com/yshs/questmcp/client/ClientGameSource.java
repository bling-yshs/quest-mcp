package com.yshs.questmcp.client;

import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.GameStatus;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Reads the game status from the Minecraft client, FTB Quests and JEI. Must run on the client main thread. */
final class ClientGameSource implements GameSource {
    /**
     * Reads client readiness after FTB's loading placeholder has been replaced by synchronized team progress.
     * @return current world, quest, recipe and language status
     */
    @Override
    public GameStatus status() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null && minecraft.player != null;
        return new GameStatus(
                inWorld,
                // FTB 2001.3+ uses a NIL_UUID team as its placeholder until team progress arrives.
                inWorld && ClientQuestFile.exists()
                        && !Util.NIL_UUID.equals(ClientQuestFile.INSTANCE.selfTeamData.getTeamId()),
                inWorld && QuestMcpJeiPlugin.runtime() != null,
                minecraft.getLanguageManager().getSelected());
    }
}
