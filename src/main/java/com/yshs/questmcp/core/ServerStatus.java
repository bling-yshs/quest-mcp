package com.yshs.questmcp.core;

import java.io.IOException;
import java.net.BindException;
import java.net.URI;

/** Whether the MCP server is serving, as reported to the player in the log and in game. */
public sealed interface ServerStatus {
    /** Not started yet, or stopped because the game is shutting down. */
    record Stopped() implements ServerStatus {
    }

    /** Serving clients at {@code url}. */
    record Running(URI url) implements ServerStatus {
    }

    /**
     * The server could not start, so nothing is listening.
     *
     * @param port   the configured port it tried to bind
     * @param detail why binding failed, in the operating system's words, e.g. {@code Address already in use}
     */
    record Failed(int port, String detail) implements ServerStatus {
        /** Explains why {@link QuestMcpServer#start} failed on {@code port}. */
        public static Failed of(int port, IOException cause) {
            // The operating system's wording ("Address already in use", or e.g. a Windows excluded-port message) is
            // the most accurate explanation of a bind failure, so it is kept as is.
            String detail = cause instanceof BindException && cause.getMessage() != null
                    ? cause.getMessage()
                    : cause.toString();
            return new Failed(port, detail);
        }

        /** What went wrong, in English for the log, e.g. {@code port 25599 is unavailable: Address already in use}. */
        public String reason() {
            return "port " + port + " is unavailable: " + detail;
        }
    }
}
