package com.yshs.questmcp.core;

import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestBook.Chapter;
import com.yshs.questmcp.core.source.QuestBook.ItemRef;
import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.QuestBook.Reward;
import com.yshs.questmcp.core.source.QuestBook.Task;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.QuestStatus;
import com.yshs.questmcp.core.source.RichText;
import java.util.List;
import java.util.Optional;

/** Supplies current-display quest fixtures and records the read requested by the real endpoint. */
final class FakeQuestSource implements QuestSource {
    private volatile QuestBook book = new QuestBook(List.of());
    volatile Optional<String> viewedQuestId = Optional.empty();
    volatile Scope lastScope;
    volatile String lastId;
    volatile boolean lastRevealHidden;
    volatile int viewedReads;

    /**
     * Replaces the snapshot returned by the fake source.
     * @param book current-display quest fixture
     */
    void set(QuestBook book) {
        this.book = book;
    }

    /**
     * Records the source request and returns the independently prepared fixture.
     * @param scope requested operation
     * @param id chapter or quest ID
     * @param revealHidden whether hidden content may be read
     * @return prepared current-language snapshot
     */
    @Override
    public QuestBook questBook(Scope scope, String id, boolean revealHidden) {
        lastScope = scope;
        lastId = id;
        lastRevealHidden = revealHidden;
        return book;
    }

    /**
     * Reads the live UI selection set by the test.
     * @return selected quest ID, or empty
     */
    @Override
    public Optional<String> viewedQuestId() {
        viewedReads++;
        return viewedQuestId;
    }

    /**
     * Builds a chapter fixture.
     * @param id chapter ID
     * @param title displayed title
     * @param visible whether the chapter is shown
     * @param quests chapter's quests in book order
     * @return immutable chapter
     */
    static Chapter chapter(String id, String title, boolean visible, Quest... quests) {
        return new Chapter(id, title, visible, List.of(quests));
    }

    /**
     * Builds a quest fixture with empty details.
     * @param id quest ID
     * @param chapterId owning chapter
     * @param title displayed title
     * @param subtitle displayed subtitle
     * @param visible whether the quest is shown
     * @param status current progress status
     * @param dependencyIds prerequisite quest IDs
     * @return immutable quest
     */
    static Quest quest(String id, String chapterId, String title, String subtitle,
            boolean visible, QuestStatus status, String... dependencyIds) {
        return new Quest(id, chapterId, title, subtitle, RichText.EMPTY, visible, false,
                false, status, List.of(dependencyIds), List.of(), List.of());
    }

    /**
     * Starts a quest fixture with empty, visible and available defaults.
     * @param id quest ID
     * @param chapterId owning chapter
     * @param title displayed title
     * @return mutable fixture builder
     */
    static QuestBuilder quest(String id, String chapterId, String title) {
        return new QuestBuilder(id, chapterId, title);
    }

    /**
     * Builds rich text from displayed description lines.
     * @param lines lines with empty strings separating paragraphs
     * @return immutable rich text
     */
    static RichText lines(String... lines) {
        RichText.Builder text = RichText.builder();
        for (String line : lines) {
            text.text(line).lineBreak();
        }
        return text.build();
    }

    /**
     * Builds a counted item fixture.
     * @param id registry ID
     * @param name displayed name
     * @param count item count
     * @return item identity
     */
    static ItemRef item(String id, String name, long count) {
        return new ItemRef(IngredientKind.ITEM, id, name, count);
    }

    /**
     * Builds a counted fluid fixture.
     * @param id registry ID
     * @param name displayed name
     * @param amount fluid amount in millibuckets
     * @return fluid identity
     */
    static ItemRef fluid(String id, String name, long amount) {
        return new ItemRef(IngredientKind.FLUID, id, name, amount);
    }

    /**
     * Builds a visible task fixture with progress.
     * @param type task type ID
     * @param title displayed title
     * @param progress current progress
     * @param maxProgress required progress
     * @param items accepted submission materials
     * @return task snapshot
     */
    static Task task(String type, String title, long progress, long maxProgress, ItemRef... items) {
        return new Task(type, title, List.of(items), progress, maxProgress, progress >= maxProgress, true);
    }

    /**
     * Builds a visible reward fixture.
     * @param type reward type ID
     * @param title displayed title
     * @param items rewarded materials
     * @return reward snapshot
     */
    static Reward reward(String type, String title, ItemRef... items) {
        return new Reward(type, title, List.of(items), true);
    }

    /** Mutable builder for independent endpoint fixtures. */
    static final class QuestBuilder {
        private final String id;
        private final String chapterId;
        private final String title;
        private String subtitle = "";
        private RichText description = RichText.EMPTY;
        private boolean visible = true;
        private boolean detailsHidden;
        private boolean descriptionHidden;
        private QuestStatus status = QuestStatus.AVAILABLE;
        private List<String> dependencyIds = List.of();
        private List<Task> tasks = List.of();
        private List<Reward> rewards = List.of();

        /**
         * Initializes quest identity and display text.
         * @param id quest ID
         * @param chapterId owning chapter
         * @param title displayed title
         */
        private QuestBuilder(String id, String chapterId, String title) {
            this.id = id;
            this.chapterId = chapterId;
            this.title = title;
        }

        /**
         * Sets the displayed subtitle.
         * @param subtitle displayed text
         * @return this builder
         */
        QuestBuilder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /**
         * Sets the displayed rich description.
         * @param description rich text
         * @return this builder
         */
        QuestBuilder description(RichText description) {
            this.description = description;
            return this;
        }

        /**
         * Marks the quest as hidden.
         * @return this builder
         */
        QuestBuilder hidden() {
            visible = false;
            return this;
        }

        /**
         * Hides details until the quest becomes startable.
         * @return this builder
         */
        QuestBuilder detailsHidden() {
            detailsHidden = true;
            return this;
        }

        /**
         * Hides the description until completion.
         * @return this builder
         */
        QuestBuilder descriptionHidden() {
            descriptionHidden = true;
            return this;
        }

        /**
         * Sets the progress status.
         * @param status quest status
         * @return this builder
         */
        QuestBuilder status(QuestStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets direct prerequisites.
         * @param dependencyIds prerequisite quest IDs
         * @return this builder
         */
        QuestBuilder dependencies(String... dependencyIds) {
            this.dependencyIds = List.of(dependencyIds);
            return this;
        }

        /**
         * Sets submission tasks.
         * @param tasks ordered task snapshots
         * @return this builder
         */
        QuestBuilder tasks(Task... tasks) {
            this.tasks = List.of(tasks);
            return this;
        }

        /**
         * Sets rewards.
         * @param rewards ordered reward snapshots
         * @return this builder
         */
        QuestBuilder rewards(Reward... rewards) {
            this.rewards = List.of(rewards);
            return this;
        }

        /**
         * Creates the immutable quest fixture.
         * @return quest snapshot
         */
        Quest build() {
            return new Quest(id, chapterId, title, subtitle, description, visible, detailsHidden, descriptionHidden,
                    status, dependencyIds, tasks, rewards);
        }
    }
}
