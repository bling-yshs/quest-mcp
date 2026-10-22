package com.yshs.questmcp.core;

import com.yshs.questmcp.core.source.GameSource;
import com.yshs.questmcp.core.source.GameStatus;

/** An in-memory game whose status the test sets; remembers which thread read it. */
final class FakeGameSource implements GameSource {
    static final GameStatus MAIN_MENU = new GameStatus(false, false, false, "en_us");

    private volatile GameStatus status = MAIN_MENU;
    private volatile String lastReadThread;

    void set(GameStatus status) {
        this.status = status;
    }

    String lastReadThread() {
        return lastReadThread;
    }

    @Override
    public GameStatus status() {
        lastReadThread = Thread.currentThread().getName();
        return status;
    }
}
