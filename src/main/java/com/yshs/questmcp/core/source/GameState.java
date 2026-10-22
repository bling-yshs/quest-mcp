package com.yshs.questmcp.core.source;

/** How far the game is towards being able to answer quest and recipe questions. */
public enum GameState {
    /** The player is in the main menu (or otherwise not in a world). */
    NOT_IN_WORLD,
    /** In a world, but the FTB Quests quest book has not been received yet. */
    QUESTS_NOT_LOADED,
    /** In a world with the quest book loaded, but JEI has not finished loading its recipes. */
    JEI_NOT_READY,
    /** In a world with both the quest book and JEI available. */
    READY
}
