package com.yshs.questmcp.core.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.QuestBook.Chapter;
import com.yshs.questmcp.core.source.QuestBook.ItemRef;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.QuestBook.Reward;
import com.yshs.questmcp.core.source.QuestBook.Task;
import com.yshs.questmcp.core.source.RichText;
import java.util.List;
import java.util.Optional;

/**
 * {@code get_quest}: one quest in full, with its description as Markdown, tasks with the player's progress, rewards,
 * prerequisites and follow-ups.
 */
public final class GetQuestTool implements Tool {
    private static final String QUEST_ID = "quest_id";
    /** How many of a task's alternative items are listed (e.g. for an item filter or tag). */
    private static final int MAX_ALTERNATIVES = 10;

    private final QuestBookReader reader;

    public GetQuestTool(QuestBookReader reader) {
        this.reader = reader;
    }

    @Override
    public String name() {
        return "get_quest";
    }

    /**
     * Describes the quest detail output and where callers can find quest IDs.
     *
     * @return the description shown to MCP clients
     */
    @Override
    public String description() {
        return "Read one quest of the FTB Quests quest book in full: title, subtitle, the player's status, the "
                + "description as Markdown, tasks with the player's team progress (e.g. 12/32), rewards, prerequisite "
                + "quests and follow-up quests (quests that need this one). Item and fluid IDs are given for tasks and "
                + "rewards. Links to other quests in the description look like [title](quest:<quest ID>); call "
                + "get_quest with that ID to follow them. Get quest IDs from get_chapter or search_quests.";
    }

    /**
     * Declares the tool arguments for current-language reads.
     * @return JSON input schema
     */
    @Override
    public JsonObject inputSchema() {
        JsonObject questId = new JsonObject();
        questId.addProperty("type", "string");
        questId.addProperty("description", "Quest ID as listed by get_chapter (16 hex digits), e.g. "
                + "\"1A2B3C4D5E6F7A8B\". Always pass it as a string.");
        JsonObject properties = new JsonObject();
        properties.add(QUEST_ID, questId);
        JsonArray required = new JsonArray();
        required.add(QUEST_ID);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    /**
     * Reads a quest and renders only the details the caller is allowed to see.
     *
     * @param arguments validated quest ID
     * @return the quest details as Markdown
     * @throws ToolException if the quest is unknown, hidden or unavailable
     */
    @Override
    public String call(JsonObject arguments) throws ToolException {
        String id = arguments.get(QUEST_ID).getAsString();
        QuestBookView book = reader.read(QuestSource.Scope.QUEST, id);
        Quest quest = book.quest(id).orElseThrow(() -> new ToolException("No quest with ID `" + id.strip()
                + "` exists. Call get_chapter to list the quests of a chapter with their IDs, or search_quests to "
                + "find a quest."));

        StringBuilder out = new StringBuilder("# ").append(book.title(quest)).append(" (`").append(quest.id())
                .append("`)");
        if (book.isHidden(quest)) {
            out.append(" (hidden)");
        }
        out.append("\n\n");
        String subtitle = book.subtitle(quest);
        if (!subtitle.isBlank()) {
            out.append("- Subtitle: ").append(subtitle).append('\n');
        }
        out.append("- Status: ").append(quest.status()).append('\n');
        Chapter chapter = book.chapter(quest);
        out.append("- Chapter: ").append(book.title(chapter)).append(" (`").append(chapter.id()).append("`)");
        if (book.isHidden(chapter)) {
            out.append(" (hidden)");
        }
        out.append('\n');
        StringBuilder notes = new StringBuilder();
        QuestMarkdown.appendNotes(notes, book);
        if (!notes.isEmpty()) {
            out.append('\n').append(notes);
        }

        boolean detailsHidden = book.isDetailsHidden(quest);
        if (detailsHidden && !book.revealHidden()) {
            out.append("\nThe quest book does not open this quest until it can be started, so its description, tasks ")
                    .append("and rewards are not known yet.\n");
        } else {
            if (detailsHidden) {
                out.append("\nThe quest book does not open this quest until it can be started; its description, ")
                        .append("tasks and rewards are shown because revealHiddenQuests is on.\n");
            }
            appendDescription(out, book, quest);
            appendTasks(out, book, quest);
            appendRewards(out, book, quest);
        }
        appendQuestList(out, book, quest, "Prerequisites", book.prerequisites(quest));
        appendQuestList(out, book, quest, "Follow-ups", book.followUps(quest));
        return out.toString();
    }

    private static void appendDescription(StringBuilder out, QuestBookView book, Quest quest) {
        out.append("\n## Description");
        if (book.isDescriptionHidden(quest) && book.revealHidden()) {
            out.append(" (hidden in the quest book until the quest is completed)");
        }
        out.append("\n\n");
        Optional<RichText> description = book.description(quest);
        if (description.isEmpty()) {
            out.append("The quest book shows the description only once the quest is completed.\n");
        } else if (description.get().isEmpty()) {
            out.append("None.\n");
        } else {
            out.append(QuestMarkdown.richText(description.get(), book)).append('\n');
        }
    }

    private static void appendTasks(StringBuilder out, QuestBookView book, Quest quest) {
        out.append("\n## Tasks\n\n");
        List<Task> tasks = book.tasks(quest);
        if (tasks.isEmpty()) {
            out.append("None.\n");
            return;
        }
        for (Task task : tasks) {
            out.append("- ").append(book.title(task)).append(" (`").append(task.type()).append("`)");
            appendItems(out, task.items(), true);
            out.append(". Progress: ").append(task.progress()).append('/').append(task.maxProgress());
            if (task.completed()) {
                out.append(" (done)");
            }
            if (book.isHidden(task)) {
                out.append(" (hidden)");
            }
            out.append('\n');
        }
        if (book.hasUnlistedTasks(quest)) {
            out.append(book.revealHidden()
                    ? "\nTasks must be done in order; the quest book lists the tasks marked (hidden) once the ones "
                            + "before them are done.\n"
                    : "\nTasks must be done in order; the quest book lists the next task once this one is done.\n");
        }
    }

    private static void appendRewards(StringBuilder out, QuestBookView book, Quest quest) {
        out.append("\n## Rewards\n\n");
        List<Reward> rewards = book.rewards(quest);
        if (rewards.isEmpty()) {
            out.append("None.\n");
            return;
        }
        for (Reward reward : rewards) {
            out.append("- ").append(book.title(reward)).append(" (`").append(reward.type()).append("`)");
            appendItems(out, reward.items(), false);
            if (book.isHidden(reward)) {
                out.append(" (hidden)");
            }
            out.append('\n');
        }
    }

    /**
     * Appends {@code : 16 × Snowball (`minecraft:snowball`)}; with several items, those of a task are alternatives and
     * those of a reward are all given.
     * @param out Markdown output
     * @param items counted materials
     * @param alternatives whether the materials are interchangeable task inputs
     */
    private static void appendItems(StringBuilder out, List<ItemRef> items, boolean alternatives) {
        if (items.isEmpty()) {
            return;
        }
        out.append(": ");
        if (alternatives && items.size() > 1) {
            ItemRef first = items.get(0);
            boolean sameAmount = items.stream().allMatch(item -> item.count() == first.count()
                    && item.kind() == first.kind());
            if (sameAmount) {
                out.append(amount(first)).append(" × any of ");
                int shown = Math.min(items.size(), MAX_ALTERNATIVES);
                for (int i = 0; i < shown; i++) {
                    out.append(i > 0 ? ", " : "").append(name(items.get(i)));
                }
                if (items.size() > shown) {
                    out.append(" and ").append(items.size() - shown).append(" more");
                }
                return;
            }
            out.append("any of ");
        }
        int shown = alternatives ? Math.min(items.size(), MAX_ALTERNATIVES) : items.size();
        for (int i = 0; i < shown; i++) {
            ItemRef item = items.get(i);
            out.append(i > 0 ? ", " : "").append(amount(item)).append(" × ").append(name(item));
        }
        if (items.size() > shown) {
            out.append(" and ").append(items.size() - shown).append(" more");
        }
    }

    private static String amount(ItemRef item) {
        return item.kind() == IngredientKind.FLUID ? item.count() + " mB" : Long.toString(item.count());
    }

    private static String name(ItemRef item) {
        return item.name().isBlank() ? "`" + item.id() + "`" : item.name() + " (`" + item.id() + "`)";
    }

    private static void appendQuestList(StringBuilder out, QuestBookView book, Quest quest, String heading,
            List<Quest> quests) {
        out.append("\n## ").append(heading).append("\n\n");
        if (quests.isEmpty()) {
            out.append("None.\n");
            return;
        }
        for (Quest other : quests) {
            out.append("- `").append(other.id()).append("` ").append(book.title(other)).append(" (")
                    .append(other.status());
            if (book.isHidden(other)) {
                out.append(", hidden");
            }
            if (!other.chapterId().equals(quest.chapterId())) {
                out.append(", chapter ").append(book.title(book.chapter(other)));
            }
            out.append(")\n");
        }
    }
}
