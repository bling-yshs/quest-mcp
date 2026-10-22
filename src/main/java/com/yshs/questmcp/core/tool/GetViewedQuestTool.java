package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.QuestSource;

/** Identifies the quest the player is reading in the active FTB Quests details panel. */
public final class GetViewedQuestTool implements Tool {
    private final QuestBookReader reader;

    /**
     * Creates the live viewed-quest lookup.
     *
     * @param reader the localized, visibility-checked quest reader
     */
    public GetViewedQuestTool(QuestBookReader reader) {
        this.reader = reader;
    }

    /**
     * Identifies the tool used to resolve "this quest".
     *
     * @return the MCP tool name
     */
    @Override
    public String name() {
        return "get_viewed_quest";
    }

    /**
     * Explains how to read the player's current quest context.
     *
     * @return the description shown to MCP clients
     */
    @Override
    public String description() {
        return "Identify the quest currently open in the player's FTB Quests details panel: quest ID, title, chapter "
                + "and status. Call this when the player asks about 'this quest' or 'the quest I am looking at', then "
                + "call get_quest with the returned ID for its full description, tasks and rewards. Returns an explicit "
                + "none when no readable quest details are open. Reads the active screen on every call; hovered, "
                + "pinned, actionable and previously viewed quests are not the current selection.";
    }

    /**
     * Declares the empty arguments for the current UI selection.
     *
     * @return the MCP input schema
     */
    @Override
    public JsonObject inputSchema() {
        JsonObject properties = new JsonObject();
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Resolves the current UI selection without exposing stale or hidden quest identities.
     *
     * @param arguments validated empty arguments
     * @return quest identity and follow-up guidance, or an explicit no-viewed-quest result
     * @throws ToolException if the game or quest book is unavailable or the game thread times out
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        QuestBookView book = reader.read(QuestSource.Scope.VIEWED, "");
        var viewed = book.viewedQuest();
        if (viewed.isEmpty()) {
            return "# Viewed quest\n\n- Viewed quest: none\n\n"
                    + "No readable quest details are currently open in the active FTB Quests screen. "
                    + "Open a quest in the quest book, then call get_viewed_quest again.\n";
        }
        var quest = viewed.get();
        var chapter = book.chapter(quest);
        StringBuilder out = new StringBuilder("# Viewed quest\n\n- Quest: `").append(quest.id()).append("` ")
                .append(book.title(quest)).append(book.isHidden(quest) ? " (hidden)" : "")
                .append("\n- Status: ").append(quest.status()).append("\n- Chapter: `").append(chapter.id())
                .append("` ").append(book.title(chapter)).append(book.isHidden(chapter) ? " (hidden)" : "")
                .append('\n');
        QuestMarkdown.appendNotes(out, book);
        return out.append("\nCall get_quest with quest_id `").append(quest.id())
                .append("` to read this quest's description, tasks, rewards and prerequisites.\n").toString();
    }
}
