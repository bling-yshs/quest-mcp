package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.QuestBook.Chapter;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import java.util.List;

/** {@code get_chapter}: the quests of one chapter with the player's status for each. */
public final class GetChapterTool implements Tool {
    private static final String CHAPTER_ID = "chapter_id";

    private final QuestBookReader reader;

    public GetChapterTool(QuestBookReader reader) {
        this.reader = reader;
    }

    @Override
    public String name() {
        return "get_chapter";
    }

    @Override
    public String description() {
        return "List the quests in one chapter of the FTB Quests quest book: quest ID, title, subtitle and the "
                + "player's status (LOCKED: prerequisites missing; AVAILABLE: can be started; IN_PROGRESS; "
                + "COMPLETED). Get chapter IDs from list_chapters.";
    }

    /**
     * Declares the tool arguments for current-language reads.
     * @return JSON input schema
     */
    @Override
    public JsonObject inputSchema() {
        JsonObject chapterId = new JsonObject();
        chapterId.addProperty("type", "string");
        chapterId.addProperty("description", "Chapter ID as listed by list_chapters, e.g. \"1A2B3C4D5E6F7A8B\"");
        JsonObject properties = new JsonObject();
        properties.add(CHAPTER_ID, chapterId);
        JsonArray required = new JsonArray();
        required.add(CHAPTER_ID);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Reads the requested data and renders the tool result.
     * @param arguments validated tool arguments
     * @return Markdown tool result
     * @throws ToolException if the operation fails
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String id = arguments.get(CHAPTER_ID).getAsString();
        QuestBookView book = reader.read(QuestSource.Scope.CHAPTER, id);
        Chapter chapter = book.chapter(id).orElseThrow(() -> new ToolException("No chapter with ID `" + id.strip()
                + "` exists. Call list_chapters to get the IDs of the chapters."));

        StringBuilder out = new StringBuilder("# ").append(book.title(chapter)).append(" (`").append(chapter.id())
                .append("`)");
        if (book.isHidden(chapter)) {
            out.append(" (hidden)");
        }
        out.append("\n\n");
        List<Quest> quests = book.quests(chapter);
        if (quests.isEmpty()) {
            return out.append("This chapter has no quests.\n").toString();
        }
        out.append(book.progress(chapter)).append(".\n");
        QuestMarkdown.appendNotes(out, book);
        out.append('\n');
        for (Quest quest : quests) {
            out.append("- `").append(quest.id()).append("` ").append(book.title(quest)).append(" (")
                    .append(quest.status());
            if (book.isHidden(quest)) {
                out.append(", hidden");
            }
            out.append(')');
            String subtitle = book.subtitle(quest);
            if (!subtitle.isBlank()) {
                out.append(": ").append(subtitle);
            }
            out.append('\n');
        }
        return out.toString();
    }
}
