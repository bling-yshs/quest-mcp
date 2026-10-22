package com.yshs.questmcp.core.tool;

import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.GameStatus;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestSource;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/** Checks readiness and captures the requested quest data and UI selection on the game thread. */
public final class QuestBookReader {
    private final GameSource game;
    private final QuestSource quests;
    private final BooleanSupplier revealHiddenQuests;
    private final GameThread gameThread;

    /**
     * Connects quest reads to game readiness, visibility and dispatch.
     * @param game current game state
     * @param quests client quest data
     * @param revealHiddenQuests current visibility setting
     * @param gameThread game-thread dispatcher
     */
    public QuestBookReader(GameSource game, QuestSource quests, BooleanSupplier revealHiddenQuests,
            GameThread gameThread) {
        this.game = game;
        this.quests = quests;
        this.revealHiddenQuests = revealHiddenQuests;
        this.gameThread = gameThread;
    }

    /**
     * Captures only the data needed by one quest operation in the game's current language.
     * @param scope operation's required data
     * @param id requested chapter or quest ID; empty for overview, search and viewed selection
     * @return visibility-filtered view of the captured data
     * @throws ToolException if the world or quest book is unavailable or the game times out
     */
    QuestBookView read(QuestSource.Scope scope, String id) throws ToolException {
        boolean reveal = revealHiddenQuests.getAsBoolean();
        Snapshot snapshot = gameThread.read(() -> {
            GameStatus status = game.status();
            if (!status.inWorld() || !status.questsLoaded()) {
                return new Snapshot(status, null, Optional.empty());
            }
            Optional<String> viewed = scope == QuestSource.Scope.VIEWED ? quests.viewedQuestId() : Optional.empty();
            String requestedId = scope == QuestSource.Scope.VIEWED ? viewed.orElse("") : id;
            return new Snapshot(status, quests.questBook(scope, requestedId, reveal), viewed);
        });
        if (!snapshot.status().inWorld()) {
            throw new ToolException("The player is not in a world, so there is no quest book to read. Quest tools "
                    + "work once the player has joined a world.");
        }
        if (snapshot.book() == null) {
            throw new ToolException("The player is in a world, but the FTB Quests quest book has not been received "
                    + "from the server yet. Try again in a moment; if this persists, FTB Quests may be missing on the "
                    + "server.");
        }
        return new QuestBookView(snapshot.book(), reveal, snapshot.viewedQuestId());
    }

    /**
     * Quest data and UI selection captured in one game-thread operation.
     * @param status game readiness
     * @param book requested quest data, or null when unavailable
     * @param viewedQuestId active quest-details selection when requested
     */
    private record Snapshot(GameStatus status, @Nullable QuestBook book, Optional<String> viewedQuestId) {
    }
}
