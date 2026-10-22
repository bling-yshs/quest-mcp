package com.yshs.questmcp.core.tool;

import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestBook.Chapter;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.QuestBook.Reward;
import com.yshs.questmcp.core.source.QuestBook.Task;
import com.yshs.questmcp.core.source.QuestStatus;
import com.yshs.questmcp.core.source.RichText;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The requested quests in the game's current language, limited to what the player can see in the quest book unless
 * {@code revealHiddenQuests} is on. Tools read chapters and quests through this view to apply visibility consistently.
 */
final class QuestBookView {
    private final QuestBook book;
    private final boolean revealHidden;
    private final Optional<String> viewedQuestId;
    private final Map<String, Chapter> chaptersById = new HashMap<>();
    private final Map<String, Quest> questsById = new HashMap<>();

    /**
     * Creates a current-language, visibility-filtered view of the book and its active UI selection.
     *
     * @param book immutable quest snapshot
     * @param revealHidden whether hidden content is revealed
     * @param viewedQuestId active quest-details selection
     */
    QuestBookView(QuestBook book, boolean revealHidden, Optional<String> viewedQuestId) {
        this.book = book;
        this.revealHidden = revealHidden;
        this.viewedQuestId = viewedQuestId;
        for (Chapter chapter : book.chapters()) {
            chaptersById.put(normalizeId(chapter.id()), chapter);
            for (Quest quest : chapter.quests()) {
                questsById.put(normalizeId(quest.id()), quest);
            }
        }
    }

    /** Whether hidden chapters and quests are included (and marked hidden). */
    boolean revealHidden() {
        return revealHidden;
    }

    /** The chapters that may be shown, in quest book order. */
    List<Chapter> chapters() {
        return book.chapters().stream().filter(this::isShown).toList();
    }

    /** The chapter with this ID, if it may be shown. IDs are matched ignoring case and a leading {@code #}. */
    Optional<Chapter> chapter(String id) {
        return Optional.ofNullable(chaptersById.get(normalizeId(id))).filter(this::isShown);
    }

    /** The quests of a shown chapter that may be shown, in quest book order. */
    List<Quest> quests(Chapter chapter) {
        return chapter.quests().stream().filter(quest -> revealHidden || quest.visible()).toList();
    }

    /** Every quest that may be shown, in quest book order (chapter by chapter). */
    List<Quest> quests() {
        return chapters().stream().flatMap(chapter -> quests(chapter).stream()).toList();
    }

    /** The quest with this ID, if it may be shown. IDs are matched ignoring case and a leading {@code #}. */
    Optional<Quest> quest(String id) {
        return Optional.ofNullable(questsById.get(normalizeId(id))).filter(this::isShown);
    }

    /**
     * Resolves the active quest-details selection through the book's visibility rules.
     *
     * @return the viewed quest, or empty for absent, stale or disallowed selections
     */
    Optional<Quest> viewedQuest() {
        return viewedQuestId.flatMap(this::quest);
    }

    /** The chapter a quest belongs to. */
    Chapter chapter(Quest quest) {
        return chaptersById.get(normalizeId(quest.chapterId()));
    }

    /** The quests that must be completed before {@code quest}, as far as they may be shown. */
    List<Quest> prerequisites(Quest quest) {
        return quest.dependencyIds().stream().map(this::quest).flatMap(Optional::stream).distinct().toList();
    }

    /** The quests that depend on {@code quest}, as far as they may be shown, in quest book order. */
    List<Quest> followUps(Quest quest) {
        String id = normalizeId(quest.id());
        return quests().stream()
                .filter(other -> other.dependencyIds().stream().anyMatch(dependency -> normalizeId(dependency)
                        .equals(id)))
                .toList();
    }

    /**
     * The quest's current-language description, if it may be shown: empty when the quest book does not show it
     * yet (see {@link #isDetailsHidden} and {@link #isDescriptionHidden}), unless hidden content is revealed.
     * @param quest requested quest
     * @return readable current description, or empty when hidden
     */
    Optional<RichText> description(Quest quest) {
        if (!revealHidden && (quest.detailsHidden() || quest.descriptionHidden())) {
            return Optional.empty();
        }
        return Optional.of(quest.description());
    }

    /**
     * The quest's tasks that may be shown: none while the quest book hides the quest's details, and only those the
     * quest book lists, unless hidden content is revealed.
     */
    List<Task> tasks(Quest quest) {
        if (revealHidden) {
            return quest.tasks();
        }
        return quest.detailsHidden() ? List.of() : quest.tasks().stream().filter(Task::visible).toList();
    }

    /** Whether the quest book lists fewer tasks than the quest has, because they must be done in order. */
    boolean hasUnlistedTasks(Quest quest) {
        return quest.tasks().stream().anyMatch(task -> !task.visible());
    }

    /**
     * The quest's rewards that may be shown: none while the quest book hides the quest's details, and only those the
     * quest book lists, unless hidden content is revealed.
     */
    List<Reward> rewards(Quest quest) {
        if (revealHidden) {
            return quest.rewards();
        }
        return quest.detailsHidden() ? List.of() : quest.rewards().stream().filter(Reward::visible).toList();
    }

    /** Whether the quest book does not open the quest yet, so its description, tasks and rewards are hidden. */
    boolean isDetailsHidden(Quest quest) {
        return quest.detailsHidden();
    }

    /** Whether the quest book does not show the quest's description until the quest is completed. */
    boolean isDescriptionHidden(Quest quest) {
        return quest.descriptionHidden();
    }

    boolean isHidden(Task task) {
        return !task.visible();
    }

    boolean isHidden(Reward reward) {
        return !reward.visible();
    }

    boolean isHidden(Chapter chapter) {
        return !chapter.visible();
    }

    boolean isHidden(Quest quest) {
        Chapter chapter = chaptersById.get(normalizeId(quest.chapterId()));
        return !quest.visible() || chapter == null || !chapter.visible();
    }

    /**
     * Reads the captured current-language title.
     * @param chapter requested chapter
     * @return displayed title
     */
    String title(Chapter chapter) {
        return chapter.title();
    }

    /**
     * Reads the captured current-language title.
     * @param quest requested quest
     * @return displayed title
     */
    String title(Quest quest) {
        return quest.title();
    }

    /**
     * Reads the captured current-language subtitle.
     * @param quest requested quest
     * @return displayed subtitle
     */
    String subtitle(Quest quest) {
        return quest.subtitle();
    }

    /**
     * Reads the captured current-language title.
     * @param task requested task
     * @return displayed title
     */
    String title(Task task) {
        return task.title();
    }

    /**
     * Reads the captured current-language title.
     * @param reward requested reward
     * @return displayed title
     */
    String title(Reward reward) {
        return reward.title();
    }

    /** E.g. {@code 5 quests, 2 completed (40%)}, counting only the quests that may be shown. */
    String progress(Chapter chapter) {
        List<Quest> quests = quests(chapter);
        if (quests.isEmpty()) {
            return "no quests";
        }
        long completed = quests.stream().filter(quest -> quest.status() == QuestStatus.COMPLETED).count();
        // Rounded down, so that 100% always means every quest is completed.
        long percent = completed * 100 / quests.size();
        return quests.size() + (quests.size() == 1 ? " quest, " : " quests, ") + completed + " completed ("
                + percent + "%)";
    }

    private boolean isShown(Chapter chapter) {
        return revealHidden || chapter.visible();
    }

    private boolean isShown(Quest quest) {
        return revealHidden || !isHidden(quest);
    }

    private static String normalizeId(String id) {
        String trimmed = id.strip();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
