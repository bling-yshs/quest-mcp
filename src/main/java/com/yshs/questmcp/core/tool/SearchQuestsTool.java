package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.QuestBook.Chapter;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.RichText;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Searches the readable quest text and item references, in quest book order. */
public final class SearchQuestsTool implements Tool {
    private final QuestBookReader reader;

    /**
     * Creates the quest search tool.
     *
     * @param reader reads the live book with language and visibility rules applied
     */
    public SearchQuestsTool(QuestBookReader reader) {
        this.reader = reader;
    }

    /**
     * Identifies this tool for MCP calls.
     *
     * @return the tool name
     */
    @Override
    public String name() {
        return "search_quests";
    }

    /**
     * Describes searchable fields and follow-up calls.
     *
     * @return the description shown to MCP clients
     */
    @Override
    public String description() {
        return "Search the player's FTB Quests quest book by a literal keyword or item/fluid registry ID, "
                + "ignoring case. Matches quest titles, subtitles, readable descriptions, and the IDs and game-language "
                + "display names of items referenced by readable tasks and rewards. Returns quest ID, title, chapter, "
                + "status and a short matching snippet, in book order. Use get_quest with a result's ID for details. "
                + "Reward-table contents are not searched. Defaults to 10 results; use offset for the next page.";
    }

    /**
     * Declares required search text, bounded paging.
     *
     * @return the MCP input schema
     */
    @Override
    public JsonObject inputSchema() {
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "Nonblank literal keyword or item/fluid ID, e.g. snowball or "
                + "minecraft:snowball.");
        JsonObject properties = new JsonObject();
        properties.add("query", query);
        Paging.addProperties(properties);
        JsonArray required = new JsonArray();
        required.add("query");
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Searches only fields the quest book allows the caller to read and renders one page.
     *
     * @param arguments validated query, paging arguments
     * @return Markdown beginning with the total and displayed range
     * @throws ToolException if the query is blank or the quest book is unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String query = arguments.get("query").getAsString().strip();
        if (query.isBlank()) {
            throw new ToolException("The query must not be blank. Enter a keyword or an item ID to search quests.");
        }
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 10;
        int offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : 0;
        QuestBookView book = reader.read(QuestSource.Scope.SEARCH, "");
        List<Quest> hits = new ArrayList<>();
        List<String> snippets = new ArrayList<>();
        for (Quest quest : book.quests()) {
            List<String> fields = new ArrayList<>();
            fields.add(book.title(quest));
            fields.add(book.subtitle(quest));
            book.description(quest).ifPresent(text -> fields.add(plainText(text, book)));
            Stream.concat(book.tasks(quest).stream().flatMap(task -> task.items().stream()),
                    book.rewards(quest).stream().flatMap(reward -> reward.items().stream()))
                    .forEach(item -> fields.add(item.name() + " (`" + item.id() + "`)"));
            for (String field : fields) {
                String text = field.replaceAll("(?U)\\s+", " ").strip();
                Matcher match = pattern.matcher(text);
                if (match.find()) {
                    hits.add(quest);
                    snippets.add(snippet(text, match.start()));
                    break;
                }
            }
        }
        int start = Math.min(offset, hits.size());
        int end = start + Math.min(limit, hits.size() - start);
        StringBuilder out = new StringBuilder();
        Paging.appendHeader(out, hits.size(), start, end);
        QuestMarkdown.appendNotes(out, book);
        if (hits.isEmpty()) {
            return out.append("\nNo matching quests.\n").toString();
        }
        if (start == end) {
            return out.append("\nNo results at this offset. Use a smaller offset to see matching quests.\n")
                    .toString();
        }
        out.append('\n');
        for (int i = start; i < end; i++) {
            Quest quest = hits.get(i);
            Chapter chapter = book.chapter(quest);
            out.append("- `").append(quest.id()).append("` ").append(book.title(quest)).append(" (")
                    .append(quest.status());
            if (book.isHidden(quest)) {
                out.append(", hidden");
            }
            out.append(") — Chapter: ").append(book.title(chapter)).append(" (`").append(chapter.id())
                    .append("`)\n  ").append(snippets.get(i)).append('\n');
        }
        return out.toString();
    }

    /**
     * Extracts visible description text without Markdown syntax, destinations or image paths.
     *
     * @param text the readable rich text
     * @param book resolves quest-link titles without exposing hidden targets
     * @return plain text with whitespace separating paragraphs and lines
     */
    private static String plainText(RichText text, QuestBookView book) {
        StringBuilder out = new StringBuilder();
        for (RichText.Paragraph paragraph : text.paragraphs()) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            for (RichText.Inline inline : paragraph.content()) {
                switch (inline) {
                    case RichText.Text plain -> out.append(plain.text());
                    case RichText.LineBreak lineBreak -> out.append(' ');
                    case RichText.QuestLink link -> out.append(book.quest(link.questId())
                            .map(book::title).orElse(link.text()));
                    case RichText.UrlLink link -> out.append(link.text().isBlank() ? link.url() : link.text());
                    case RichText.Image image -> out.append(' ');
                }
            }
        }
        return out.toString();
    }

    /**
     * Keeps at most 160 Unicode code points around the first match, marking omitted text.
     *
     * @param text the single-line searchable field
     * @param matchStart the first match's UTF-16 index in the original text
     * @return a compact snippet without splitting a surrogate pair
     */
    private static String snippet(String text, int matchStart) {
        int length = text.codePointCount(0, text.length());
        int match = text.codePointCount(0, matchStart);
        int start = Math.max(0, match - 60);
        int end = Math.min(length, start + 160);
        start = Math.max(0, end - 160);
        return (start > 0 ? "…" : "") + text.substring(text.offsetByCodePoints(0, start),
                text.offsetByCodePoints(0, end)).strip() + (end < length ? "…" : "");
    }
}
