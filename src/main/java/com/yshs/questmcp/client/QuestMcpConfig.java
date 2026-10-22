package com.yshs.questmcp.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** The client config file, {@code config/questmcp-client.toml}. */
public final class QuestMcpConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue PORT = BUILDER
            .comment("Port of the MCP server at http://127.0.0.1:<port>/mcp. Applied when the MCP server starts or is restarted.")
            .defineInRange("port", 25599, 1024, 65535);

    public static final ForgeConfigSpec.ConfigValue<String> TOKEN = BUILDER
            .comment("Optional bearer token. When set, MCP clients must send the header",
                    "\"Authorization: Bearer <token>\", so that other programs on this computer cannot query the game.",
                    "Empty disables the check.",
                    "Applied when the MCP server starts or is restarted.")
            .define("token", "");

    public static final ForgeConfigSpec.BooleanValue REVEAL_HIDDEN_QUESTS = BUILDER
            .comment("Whether MCP clients may see the chapters and quests the player cannot see in the FTB Quests",
                    "quest book (e.g. invisible quests, or quests hidden until their prerequisites are done).",
                    "When true they are included and marked as hidden. Off by default, so that an AI agent cannot",
                    "spoil secret progression; a modpack author can turn it on to expose the whole quest book.",
                    "Applied immediately.")
            .define("revealHiddenQuests", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private QuestMcpConfig() {
    }
}
