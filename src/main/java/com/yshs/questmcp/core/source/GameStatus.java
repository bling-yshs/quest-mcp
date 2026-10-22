package com.yshs.questmcp.core.source;

import java.util.Objects;

/**
 * A snapshot of the live game, read on the game thread.
 *
 * @param inWorld      whether the player is in a world
 * @param questsLoaded whether the FTB Quests quest book has been received
 * @param jeiReady     whether the JEI runtime is available
 * @param language     the game's current language code, e.g. {@code en_us}
 */
public record GameStatus(boolean inWorld, boolean questsLoaded, boolean jeiReady, String language) {
    public GameStatus {
        Objects.requireNonNull(language, "language");
    }

    public GameState state() {
        if (!inWorld) {
            return GameState.NOT_IN_WORLD;
        }
        if (!questsLoaded) {
            return GameState.QUESTS_NOT_LOADED;
        }
        if (!jeiReady) {
            return GameState.JEI_NOT_READY;
        }
        return GameState.READY;
    }
}
