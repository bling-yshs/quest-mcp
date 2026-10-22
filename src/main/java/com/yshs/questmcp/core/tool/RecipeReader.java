package com.yshs.questmcp.core.tool;

import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.RecipeSource;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Checks JEI readiness and performs all recipe-source reads on the game thread. */
public final class RecipeReader {
    private final GameSource game;
    private final RecipeSource recipes;
    private final GameThread gameThread;

    /**
     * Connects JEI-backed reads to game readiness and dispatch.
     * @param game current game state
     * @param recipes the JEI source
     * @param gameThread game-thread dispatcher
     */
    public RecipeReader(GameSource game, RecipeSource recipes, GameThread gameThread) {
        this.game = game;
        this.recipes = recipes;
        this.gameThread = gameThread;
    }

    /**
     * Performs a source operation only when the player is in a world and JEI is ready.
     * @param operation the current-language read operation
     * @param <T> the neutral snapshot type
     * @return the source operation's result
     * @throws ToolException if the game or JEI is unavailable or times out
     */
    <T> T read(Function<RecipeSource, T> operation) throws ToolException {
        Snapshot<T> snapshot = gameThread.read(() -> {
            GameStatus status = game.status();
            return new Snapshot<>(status, status.inWorld() && status.jeiReady()
                    ? operation.apply(recipes) : null);
        });
        if (!snapshot.status().inWorld()) {
            throw new ToolException("The player is not in a world. Item and recipe tools work after joining a world.");
        }
        if (!snapshot.status().jeiReady()) {
            throw new ToolException("JEI_NOT_READY: JEI is still loading or unavailable. Try again once JEI is ready.");
        }
        return snapshot.value();
    }

    /**
     * Stores readiness together with the data read in the same game-thread operation.
     * @param status readiness at the time of the read
     * @param value neutral source data
     * @param <T> result type
     */
    private record Snapshot<T>(GameStatus status, @Nullable T value) {
    }
}
