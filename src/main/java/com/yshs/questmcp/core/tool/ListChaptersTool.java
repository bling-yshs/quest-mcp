package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.QuestBook.Chapter;
import java.util.List;

/** {@code list_chapters}: the quest book's chapters with the player's completion. */
public final class ListChaptersTool implements Tool {
    private final QuestBookReader reader;

    public ListChaptersTool(QuestBookReader reader) {
        this.reader = reader;
    }

    @Override
    public String name() {
        return "list_chapters";
    }

    @Override
    public String description() {
        return "List the chapters of the player's FTB Quests quest book in book order: chapter ID, title, number of "
                + "quests and how many the player's team has completed. Use this for an overview of the modpack's "
                + "progression, then call get_chapter with a chapter ID to see its quests.";
    }

    /**
     * Declares the tool arguments for current-language reads.
     * @return JSON input schema
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
     * Reads the requested data and renders the tool result.
     * @param arguments validated tool arguments
     * @return Markdown tool result
     * @throws ToolException if the operation fails
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        QuestBookView book = reader.read(QuestSource.Scope.OVERVIEW, "");
        List<Chapter> chapters = book.chapters();
        StringBuilder out = new StringBuilder("# Quest book chapters\n\n");
        if (chapters.isEmpty()) {
            return out.append("The quest book has no chapters.\n").toString();
        }
        out.append(chapters.size()).append(chapters.size() == 1 ? " chapter" : " chapters").append(".\n");
        QuestMarkdown.appendNotes(out, book);
        out.append('\n');
        for (Chapter chapter : chapters) {
            out.append("- `").append(chapter.id()).append("` ").append(book.title(chapter));
            if (book.isHidden(chapter)) {
                out.append(" (hidden)");
            }
            out.append(": ").append(book.progress(chapter)).append('\n');
        }
        return out.toString();
    }
}
