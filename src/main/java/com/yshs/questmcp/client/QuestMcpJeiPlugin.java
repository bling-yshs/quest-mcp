package com.yshs.questmcp.client;

import com.yshs.questmcp.QuestMcp;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Captures the JEI runtime while JEI has one, i.e. while the player is in a world and JEI has loaded. */
@JeiPlugin
public final class QuestMcpJeiPlugin implements IModPlugin {
    private static volatile @Nullable IJeiRuntime runtime;

    /** The JEI runtime, or {@code null} while JEI is not ready. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(QuestMcp.MOD_ID, "main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
