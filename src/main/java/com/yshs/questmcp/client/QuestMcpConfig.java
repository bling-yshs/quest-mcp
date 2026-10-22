package com.yshs.questmcp.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The client config file, {@code config/questmcp-client.toml}. */
public final class QuestMcpConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PORT = BUILDER
            .comment("Port of the MCP server at http://127.0.0.1:<port>/mcp. Applied when the MCP server starts or is restarted.")
            .defineInRange("port", 25599, 1024, 65535);

    public static final ModConfigSpec.ConfigValue<String> TOKEN = BUILDER
            .comment("Optional bearer token. When set, MCP clients must send the header",
                    "\"Authorization: Bearer <token>\", so that other programs on this computer cannot query the game.",
                    "Empty disables the check.",
                    "Applied when the MCP server starts or is restarted.")
            .define("token", "");

    public static final ModConfigSpec.BooleanValue REVEAL_HIDDEN_QUESTS = BUILDER
            .comment("Whether MCP clients may see the chapters and quests the player cannot see in the FTB Quests",
                    "quest book (e.g. invisible quests, or quests hidden until their prerequisites are done).",
                    "When true they are included and marked as hidden. Off by default, so that an AI agent cannot",
                    "spoil secret progression; a modpack author can turn it on to expose the whole quest book.",
                    "Applied immediately.")
            .define("revealHiddenQuests", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuestMcpConfig() {
    }
}
