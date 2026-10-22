package com.yshs.questmcp.core.source;

import java.util.Optional;

/** Reads current-language quest snapshots on the client game thread after quest synchronization. */
public interface QuestSource {
    /** The data required by each quest operation. */
    enum Scope {
        OVERVIEW,
        CHAPTER,
        QUEST,
        SEARCH,
        VIEWED
    }

    /**
     * Reads only the requested quest data, with lightweight related quests for navigation.
     * @param scope requested operation; only QUEST and SEARCH read detailed materials
     * @param id chapter or quest ID; unused for OVERVIEW and SEARCH
     * @param revealHidden whether hidden details may be read
     * @return immutable snapshot using the text currently displayed by the game
     */
    QuestBook questBook(Scope scope, String id, boolean revealHidden);

    /**
     * Reads the quest currently open in the active quest-details panel.
     * @return selected quest ID, or empty for a closed or unrelated screen
     */
    Optional<String> viewedQuestId();
}
