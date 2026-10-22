package com.yshs.questmcp.core.mcp;

import java.util.List;
import javax.annotation.Nullable;

/** The MCP protocol revisions this server speaks: the handshake-based Streamable HTTP revisions. */
public final class ProtocolVersions {
    /** Supported revisions, oldest first. */
    public static final List<String> SUPPORTED = List.of("2025-03-26", "2025-06-18", "2025-11-25");
    public static final String LATEST = SUPPORTED.getLast();

    private ProtocolVersions() {
    }

    public static boolean isSupported(@Nullable String version) {
        return version != null && SUPPORTED.contains(version);
    }

    /** The version to answer an {@code initialize} with: the client's if supported, otherwise the newest one. */
    public static String negotiate(@Nullable String requested) {
        return isSupported(requested) ? requested : LATEST;
    }
}
