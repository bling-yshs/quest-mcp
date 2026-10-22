package com.yshs.questmcp.core.source;

/** Where the local player's team stands with a quest. */
public enum QuestStatus {
    /** The quest's tasks cannot be worked on yet, usually because prerequisites are incomplete. */
    LOCKED,
    /** The quest can be worked on, but no task has progress yet. */
    AVAILABLE,
    /** Some task has progress, but the quest is not complete. */
    IN_PROGRESS,
    COMPLETED
}
