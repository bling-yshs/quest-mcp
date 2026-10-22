package com.yshs.questmcp.core.source;

import java.util.List;
import java.util.Objects;

/**
 * The requested subset of the quest book, with current display text and visibility metadata.
 *
 * @param chapters the chapters in the order the quest book lists them
 */
public record QuestBook(List<Chapter> chapters) {
    /**
     * Copies the requested chapters into an immutable snapshot.
     * @param chapters requested chapters in book order
     */
    public QuestBook {
        chapters = List.copyOf(chapters);
    }

    /**
     * A chapter of the quest book.
     *
     * @param id      the FTB Quests ID as 16 upper-case hex digits, e.g. {@code 1A2B3C4D5E6F7A8B}
     * @param title   the chapter title as plain text
     * @param visible whether the quest book lists the chapter for the local player's team
     * @param quests  the chapter's own quests, in quest book order (quests linked in from other chapters are not
     *                included)
     */
    public record Chapter(String id, String title, boolean visible, List<Quest> quests) {
        /**
         * Validates and copies the chapter snapshot.
         * @param id chapter ID
         * @param title current displayed title
         * @param visible whether the chapter is shown
         * @param quests requested quests in book order
         */
        public Chapter {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            quests = List.copyOf(quests);
        }
    }

    /**
     * A quest, with the local player's team progress.
     *
     * @param id                 the FTB Quests ID as 16 upper-case hex digits
     * @param chapterId          the ID of the chapter the quest belongs to
     * @param title              the quest title as plain text
     * @param subtitle           the quest subtitle as plain text; empty if it has none
     * @param description        the quest description; empty if it has none
     * @param visible            whether the quest book shows the quest on its chapter's page to the local player's
     *                           team; a quest in a chapter that is not visible counts as hidden either way
     * @param detailsHidden      whether the quest book does not open the quest yet, so that its description, tasks
     *                           and rewards cannot be seen (FTB Quests' "hide details until startable", while the
     *                           quest cannot be started)
     * @param descriptionHidden  whether the quest book does not show the description yet (FTB Quests' "hide text
     *                           until complete", while the quest is not completed)
     * @param status             the team's progress
     * @param dependencyIds      the IDs of the quests that must be completed first
     * @param tasks              the quest's tasks in quest book order
     * @param rewards            the quest's rewards in quest book order
     */
    public record Quest(String id, String chapterId, String title, String subtitle,
            RichText description, boolean visible, boolean detailsHidden, boolean descriptionHidden,
            QuestStatus status, List<String> dependencyIds, List<Task> tasks, List<Reward> rewards) {
        /**
         * Validates and copies the quest snapshot.
         * @param id quest ID
         * @param chapterId owning chapter ID
         * @param title current displayed title
         * @param subtitle current displayed subtitle
         * @param description requested readable description, or empty
         * @param visible whether the quest is shown
         * @param detailsHidden whether details are hidden until startable
         * @param descriptionHidden whether text is hidden until completed
         * @param status current progress status
         * @param dependencyIds direct prerequisite quest IDs
         * @param tasks requested task details, or empty
         * @param rewards requested reward details, or empty
         */
        public Quest {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(chapterId, "chapterId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(subtitle, "subtitle");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(status, "status");
            dependencyIds = List.copyOf(dependencyIds);
            tasks = List.copyOf(tasks);
            rewards = List.copyOf(rewards);
        }
    }

    /**
     * A task of a quest, with the local player's team progress.
     *
     * @param type        the FTB Quests task type, e.g. {@code ftbquests:item}
     * @param title       the task title as plain text, e.g. {@code 16x Snowball}
     * @param items       the items or fluids the task asks for; when there are several (e.g. an item filter), any of
     *                    them counts. Empty for tasks that ask for no item or fluid
     * @param progress    how far the team has got, in the task's own unit (items, mB, kills…)
     * @param maxProgress the progress at which the task is done
     * @param completed   whether the team has completed the task
     * @param visible     whether the quest book lists the task; when tasks must be done in order, it lists those up
     *                    to the first incomplete one
     */
    public record Task(String type, String title, List<ItemRef> items, long progress, long maxProgress,
            boolean completed, boolean visible) {
        /**
         * Validates and copies the task snapshot.
         * @param type task type ID
         * @param title current displayed title
         * @param items readable accepted materials
         * @param progress current progress
         * @param maxProgress required progress
         * @param completed whether the task is complete
         * @param visible whether the task is listed
         */
        public Task {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(title, "title");
            items = List.copyOf(items);
        }
    }

    /**
     * A reward of a quest.
     *
     * @param type    the FTB Quests reward type, e.g. {@code ftbquests:item}
     * @param title   the reward title as plain text, e.g. {@code 2x Apple}
     * @param items   the items the reward gives; empty for rewards that give no fixed item (experience, commands,
     *                reward tables…)
     * @param visible whether the quest book lists the reward (it leaves out invisible rewards and, when the team's
     *                rewards are blocked, blocked ones)
     */
    public record Reward(String type, String title, List<ItemRef> items, boolean visible) {
        /**
         * Validates and copies the reward snapshot.
         * @param type reward type ID
         * @param title current displayed title
         * @param items fixed reward materials
         * @param visible whether the reward is listed
         */
        public Reward {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(title, "title");
            items = List.copyOf(items);
        }
    }

    /**
     * An item or fluid a task or reward refers to.
     *
     * @param kind  item or fluid
     * @param id    the registry ID, e.g. {@code minecraft:snowball}
     * @param name  the display name in the game's current language
     * @param count the number of items, or the amount of fluid in mB
     */
    public record ItemRef(IngredientKind kind, String id, String name, long count) {
        public ItemRef {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }
}
