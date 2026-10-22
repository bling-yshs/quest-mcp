package com.yshs.questmcp.core.source;

/**
 * Reads the live state of the game. Implementations touch game objects, so callers must only invoke them on the game
 * thread (see {@link com.yshs.questmcp.core.tool.GameThread}).
 */
public interface GameSource {
    GameStatus status();
}
