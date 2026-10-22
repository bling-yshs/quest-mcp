package com.yshs.questmcp.core.tool;

import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.QuestStatus;
import java.util.ArrayList;
import java.util.List;

/** Lists work in progress, available quests and readable quests one prerequisite step away. */
public final class GetCurrentQuestsTool implements Tool {
    private final QuestBookReader reader;

    /**
     * Creates the current quest tool.
     * @param reader the reader applying language and visibility rules
     */
    public GetCurrentQuestsTool(QuestBookReader reader) {
        this.reader = reader;
    }

    /** @return the MCP tool name */
    @Override
    public String name() {
        return "get_current_quests";
    }

    /** @return the description of ordering and the one-step rule */
    @Override
    public String description() {
        return "List quests to work on next: IN_PROGRESS first, then AVAILABLE, then LOCKED quests whose every "
                + "incomplete prerequisite is itself AVAILABLE or IN_PROGRESS. Each entry has ID, title, chapter "
                + "and status; locked entries name the blocking prerequisites. Completed quests are excluded; hidden quests are "
                + "excluded unless hidden content is revealed. Use get_quest for details.";
    }

    /** @return bounded paging schema */
    @Override
    public JsonObject inputSchema() {
        JsonObject properties = new JsonObject();
        Paging.addProperties(properties);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Renders one page of actionable quests in priority and book order.
     * @param arguments validated paging
     * @return Markdown with total count, displayed range and blocking prerequisites
     * @throws ToolException if the quest book is unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        QuestBookView book = reader.read(QuestSource.Scope.OVERVIEW, "");
        List<Quest> shown = book.quests();
        List<Quest> matches = new ArrayList<>();
        for (QuestStatus status : List.of(QuestStatus.IN_PROGRESS, QuestStatus.AVAILABLE, QuestStatus.LOCKED)) {
            for (Quest quest : shown) {
                if (quest.status() != status) {
                    continue;
                }
                if (status == QuestStatus.LOCKED) {
                    // Missing or hidden prerequisites cannot establish that this quest is one step away.
                    if (quest.dependencyIds().isEmpty()
                            || quest.dependencyIds().stream().anyMatch(id -> book.quest(id).isEmpty())) {
                        continue;
                    }
                    List<Quest> blocking = book.prerequisites(quest).stream()
                            .filter(dependency -> dependency.status() != QuestStatus.COMPLETED).toList();
                    if (blocking.isEmpty() || blocking.stream().anyMatch(dependency ->
                            dependency.status() != QuestStatus.AVAILABLE
                                    && dependency.status() != QuestStatus.IN_PROGRESS)) {
                        continue;
                    }
                }
                matches.add(quest);
            }
        }
        int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 10;
        int start = Math.min(arguments.has("offset") ? arguments.get("offset").getAsInt() : 0, matches.size());
        int end = start + Math.min(limit, matches.size() - start);
        StringBuilder out = new StringBuilder();
        Paging.appendHeader(out, matches.size(), start, end);
        QuestMarkdown.appendNotes(out, book);
        if (start == end) {
            return out.append(matches.isEmpty() ? "\nNo current quests.\n" : "\nNo results at this offset.\n")
                    .toString();
        }
        out.append('\n');
        for (Quest quest : matches.subList(start, end)) {
            var chapter = book.chapter(quest);
            out.append("- `").append(quest.id()).append("` ").append(book.title(quest)).append(" (")
                    .append(quest.status()).append(book.isHidden(quest) ? ", hidden" : "").append(") — Chapter: ")
                    .append(book.title(chapter)).append(" (`").append(chapter.id()).append("`)\n");
            if (quest.status() == QuestStatus.LOCKED) {
                out.append("  One step away; blocked by: ");
                List<Quest> blocking = book.prerequisites(quest).stream()
                        .filter(dependency -> dependency.status() != QuestStatus.COMPLETED).toList();
                for (int i = 0; i < blocking.size(); i++) {
                    Quest dependency = blocking.get(i);
                    out.append(i > 0 ? ", " : "").append('`').append(dependency.id()).append("` ")
                            .append(book.title(dependency)).append(" (").append(dependency.status()).append(')');
                }
                out.append('\n');
            }
        }
        return out.toString();
    }
}
