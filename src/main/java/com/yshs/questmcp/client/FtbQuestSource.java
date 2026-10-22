package com.yshs.questmcp.client;

import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.QuestBook;
import com.yshs.questmcp.core.source.QuestSource;
import com.yshs.questmcp.core.source.QuestStatus;
import com.yshs.questmcp.core.source.RichText;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.task.FluidTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Reads scoped snapshots from the synchronized FTB quest book on the client game thread. */
final class FtbQuestSource implements QuestSource {
    /**
     * Selects quest objects through FTB's public lookup and relationship APIs before reading details.
     * @param scope requested tool operation
     * @param id chapter or quest ID, including the optional leading hash
     * @param revealHidden whether the caller may read hidden details
     * @return immutable current-language snapshot in quest book order
     */
    @Override
    public QuestBook questBook(Scope scope, String id, boolean revealHidden) {
        ClientQuestFile file = ClientQuestFile.getInstance();
        TeamData team = file.selfTeamData;
        Map<Chapter, List<Quest>> selected = new LinkedHashMap<>();
        Map<Quest, QuestBook.Quest> details = new HashMap<>();
        String normalized = id.strip();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (scope == Scope.QUEST || scope == Scope.VIEWED) {
            Quest target = QuestObjectBase.parseHexId(normalized).map(file::getQuest).orElse(null);
            if (target == null) {
                return new QuestBook(List.of());
            }
            LinkedHashSet<Quest> related = new LinkedHashSet<>();
            related.add(target);
            if (scope == Scope.QUEST) {
                Chapter chapter = target.getChapter();
                boolean chapterVisible = chapter.hasAnyVisibleChildren() && chapter.isVisible(team);
                QuestBook.Quest snapshot = quest(team, target, chapterVisible, true, revealHidden);
                details.put(target, snapshot);
                target.streamDependencies().filter(value -> value instanceof Quest)
                        .map(value -> (Quest) value).forEach(related::add);
                target.getDependants().stream().filter(value -> value instanceof Quest)
                        .map(value -> (Quest) value).forEach(related::add);
                for (RichText.Paragraph paragraph : snapshot.description().paragraphs()) {
                    for (RichText.Inline inline : paragraph.content()) {
                        if (inline instanceof RichText.QuestLink link) {
                            QuestObjectBase.parseHexId(link.questId()).map(file::getQuest).ifPresent(related::add);
                        }
                    }
                }
            }
            for (Quest value : related) {
                selected.computeIfAbsent(value.getChapter(), ignored -> new ArrayList<>()).add(value);
            }
            // FTB stores dependants in a set; restore the same chapter and quest order used by the book.
            List<Chapter> chapterOrder = file.getAllChapters();
            List<Chapter> ordered = new ArrayList<>(selected.keySet());
            ordered.sort(Comparator.comparingInt(chapterOrder::indexOf));
            Map<Chapter, List<Quest>> sorted = new LinkedHashMap<>();
            for (Chapter chapter : ordered) {
                List<Quest> values = selected.get(chapter);
                List<Quest> questOrder = chapter.getQuests();
                values.sort(Comparator.comparingInt(questOrder::indexOf));
                sorted.put(chapter, values);
            }
            selected = sorted;
        } else if (scope == Scope.CHAPTER) {
            Chapter chapter = QuestObjectBase.parseHexId(normalized).map(file::getChapter).orElse(null);
            if (chapter != null) {
                selected.put(chapter, chapter.getQuests());
            }
        } else {
            for (Chapter chapter : file.getAllChapters()) {
                selected.put(chapter, chapter.getQuests());
            }
        }
        List<QuestBook.Chapter> chapters = new ArrayList<>();
        for (var entry : selected.entrySet()) {
            Chapter chapter = entry.getKey();
            boolean visible = chapter.hasAnyVisibleChildren() && chapter.isVisible(team);
            List<QuestBook.Quest> quests = new ArrayList<>();
            for (Quest value : entry.getValue()) {
                QuestBook.Quest snapshot = details.get(value);
                quests.add(snapshot != null ? snapshot
                        : quest(team, value, visible, scope == Scope.SEARCH, revealHidden));
            }
            chapters.add(new QuestBook.Chapter(chapter.getCodeString(), plain(chapter.getTitle()), visible, quests));
        }
        return new QuestBook(chapters);
    }

    /**
     * Reads the active quest-details panel's live selection.
     * @return current quest ID, or empty when quest details are closed
     */
    @Override
    public Optional<String> viewedQuestId() {
        QuestScreen screen = ClientUtils.getCurrentGuiAs(QuestScreen.class);
        return screen == null ? Optional.empty()
                : Optional.ofNullable(screen.getViewedQuest()).map(quest -> quest.getCodeString());
    }

    /**
     * Captures basic quest metadata and reads detailed fields only when requested and readable.
     * @param team local player's team
     * @param quest FTB quest object
     * @param chapterVisible whether its chapter is shown
     * @param details whether descriptions, tasks and rewards are required
     * @param revealHidden whether hidden details may be read
     * @return current-language quest snapshot
     */
    private static QuestBook.Quest quest(TeamData team, Quest quest, boolean chapterVisible, boolean details,
            boolean revealHidden) {
        boolean visible = quest.isVisible(team);
        boolean detailsHidden = quest.hideDetailsUntilStartable() && !team.canStartTasks(quest);
        boolean descriptionHidden = quest.getHideTextUntilComplete().get(quest.getChapter().isHideTextUntilComplete())
                && !team.isCompleted(quest);
        boolean readable = details && (revealHidden || chapterVisible && visible && !detailsHidden);
        RichText description = readable && (revealHidden || !descriptionHidden)
                ? RichTextConverter.convert(quest.getRawDescription(), quest.getDescription()) : RichText.EMPTY;
        return new QuestBook.Quest(quest.getCodeString(), quest.getChapter().getCodeString(),
                plain(quest.getTitle()), plain(quest.getSubtitle()), description, visible, detailsHidden,
                descriptionHidden, status(team, quest), quest.streamDependencies()
                        .filter(dependency -> dependency instanceof Quest)
                        .map(dependency -> dependency.getCodeString()).toList(),
                readable ? tasks(team, quest, revealHidden) : List.of(),
                readable ? rewards(team, quest, revealHidden) : List.of());
    }

    /**
     * Reads the same completion and startability state shown by the quest book.
     * @param team local player's team
     * @param quest requested quest
     * @return current quest status
     */
    private static QuestStatus status(TeamData team, Quest quest) {
        if (team.isCompleted(quest)) {
            return QuestStatus.COMPLETED;
        }
        if (!team.canStartTasks(quest)) {
            return QuestStatus.LOCKED;
        }
        return team.isStarted(quest) ? QuestStatus.IN_PROGRESS : QuestStatus.AVAILABLE;
    }

    /**
     * Reads the selected quest's task progress and materials, respecting sequential task visibility.
     * @param team local player's team
     * @param quest requested quest
     * @param revealHidden whether unlisted task materials may be read
     * @return task snapshots with complete progress and readable materials
     */
    private static List<QuestBook.Task> tasks(TeamData team, Quest quest, boolean revealHidden) {
        List<QuestBook.Task> tasks = new ArrayList<>();
        boolean listed = true;
        for (Task task : quest.getTasks()) {
            boolean completed = team.isCompleted(task);
            tasks.add(new QuestBook.Task(task.getType().getTypeId().toString(), plain(task.getTitle()),
                    listed || revealHidden ? taskItems(task) : List.of(), team.getProgress(task), task.getMaxProgress(),
                    completed, listed));
            if (quest.getRequireSequentialTasks() && !completed) {
                listed = false;
            }
        }
        return tasks;
    }

    /**
     * Reads accepted submission materials using FTB's built-in item matching method.
     * @param task selected submission task
     * @return accepted item alternatives or the required fluid
     */
    private static List<QuestBook.ItemRef> taskItems(Task task) {
        if (task instanceof ItemTask itemTask) {
            if (itemTask.getItemStack().isEmpty()) {
                return List.of();
            }
            return itemTask.getValidDisplayItems().stream()
                    .filter(stack -> !stack.isEmpty())
                    .map(stack -> item(stack, task.getMaxProgress()))
                    .distinct().toList();
        }
        if (task instanceof FluidTask fluidTask) {
            return List.of(new QuestBook.ItemRef(IngredientKind.FLUID,
                    BuiltInRegistries.FLUID.getKey(fluidTask.getFluid()).toString(),
                    plain(fluidTask.getFluid().getFluidType().getDescription()), fluidTask.getMaxProgress()));
        }
        return List.of();
    }

    /**
     * Reads the selected quest's fixed rewards using the book's visibility rules.
     * @param team local player's team
     * @param quest requested quest
     * @param revealHidden whether invisible rewards may be read
     * @return readable reward snapshots
     */
    private static List<QuestBook.Reward> rewards(TeamData team, Quest quest, boolean revealHidden) {
        List<QuestBook.Reward> rewards = new ArrayList<>();
        for (Reward reward : quest.getRewards()) {
            boolean visible = !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE;
            if (!visible && !revealHidden) {
                continue;
            }
            List<QuestBook.ItemRef> items = reward instanceof ItemReward itemReward && !itemReward.getItem().isEmpty()
                    ? List.of(item(itemReward.getItem(), itemReward.getCount())) : List.of();
            rewards.add(new QuestBook.Reward(reward.getType().getTypeId().toString(), plain(reward.getTitle()),
                    items, visible));
        }
        return rewards;
    }

    /**
     * Captures a counted material in the game's current language.
     * @param stack material stack
     * @param count required or rewarded amount
     * @return immutable material identity
     */
    private static QuestBook.ItemRef item(ItemStack stack, long count) {
        return new QuestBook.ItemRef(IngredientKind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                plain(stack.getHoverName()), count);
    }

    /**
     * Resolves a displayed component with Minecraft's already-loaded current language.
     * @param component displayed game text
     * @return plain text without formatting codes
     */
    private static String plain(Component component) {
        String text = ChatFormatting.stripFormatting(component.getString());
        return text == null ? "" : text.strip();
    }
}
