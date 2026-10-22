package com.yshs.questmcp.client;

import com.yshs.questmcp.core.source.RichText;
import dev.ftb.mods.ftblibrary.client.util.ImageComponent;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an FTB Quests description into neutral {@link RichText}, keeping what its quest view makes of it
 * ({@code ViewQuestPanel}) apart from styles:
 *
 * <ul>
 *     <li>each description line is a line; an empty line separates paragraphs, and so does a {@code {@pagebreak}}
 *     line (the quest view shows the text after it on the next page);</li>
 *     <li>FTB's text formats are parsed by FTB itself ({@code TextUtils.parseRawText}): raw JSON text components, and
 *     otherwise FTB Library's format with {@code &}/{@code §} colour and format codes, {@code \n}, translation keys in
 *     braces, {@code {image:...}} and {@code {open_url:... text:...}};</li>
 *     <li>text whose click event is FTB Quests' quest link ({@code ftbquests:link}, payload {@code quest_id}) becomes
 *     a quest link; a link to a quest link object leads to its quest; links to other objects (e.g. chapters) stay
 *     plain text;</li>
 *     <li>text whose click event opens a URL becomes a URL link;</li>
 *     <li>an image component becomes an image with its texture reference;</li>
 *     <li>all other click and hover events (docs links, commands, hover text) are dropped, keeping the text.</li>
 * </ul>
 *
 * <p>A line that cannot be converted is kept as its plain text.
 */
final class RichTextConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RichTextConverter.class);

    private RichTextConverter() {
    }

    /**
     * @param rawLines    the description lines as written in the quest book
     * @param parsedLines the same lines as FTB parsed them, in the same order
     */
    static RichText convert(List<String> rawLines, List<Component> parsedLines) {
        RichText.Builder text = RichText.builder();
        for (int i = 0; i < rawLines.size(); i++) {
            String raw = rawLines.get(i);
            // The quest view splits pages on lines that are exactly the page break code (Quest.buildDescriptionIndex).
            if (raw.equals(Quest.PAGEBREAK_CODE)) {
                text.paragraphBreak();
                continue;
            }
            List<Consumer<RichText.Builder>> pieces = new ArrayList<>();
            try {
                Component line = i < parsedLines.size() ? parsedLines.get(i) : Component.literal(raw);
                append(line, Style.EMPTY, pieces);
            } catch (RuntimeException e) {
                LOGGER.debug("Could not convert quest description line {}", raw, e);
                pieces.clear();
                pieces.add(builder -> builder.text(strip(raw)));
            }
            pieces.forEach(piece -> piece.accept(text));
            text.lineBreak();
        }
        return text.build();
    }

    /** Walks the component tree like {@link Component#visit}, also catching image components. */
    private static void append(Component component, Style parentStyle, List<Consumer<RichText.Builder>> pieces) {
        Style style = component.getStyle().applyTo(parentStyle);
        if (component.getContents() instanceof ImageComponent image) {
            String path = image.imageStr();
            pieces.add(builder -> builder.image(path));
        } else {
            component.getContents().visit((pieceStyle, piece) -> {
                pieces.add(piece(pieceStyle, strip(piece)));
                return Optional.empty();
            }, style);
        }
        for (Component sibling : component.getSiblings()) {
            append(sibling, style, pieces);
        }
    }

    private static Consumer<RichText.Builder> piece(Style style, String text) {
        switch (style.getClickEvent()) {
            case ClickEvent.OpenUrl openUrl -> {
                String url = openUrl.uri().toString();
                return builder -> builder.urlLink(url, text);
            }
            case ClickEvent.Custom custom when custom.id().equals(MultilineTextEditorScreen.QUEST_LINK_ACTION) -> {
                Optional<String> questId = linkedQuest(custom);
                if (questId.isPresent()) {
                    return builder -> builder.questLink(questId.get(), text);
                }
                return builder -> builder.text(text);
            }
            case null, default -> {
                return builder -> builder.text(text);
            }
        }
    }

    /** The quest a quest link opens, as the quest view resolves it (ImageClickAction.openQuest). */
    private static Optional<String> linkedQuest(ClickEvent.Custom link) {
        return link.payload()
                .flatMap(tag -> tag.asCompound())
                .map(compound -> compound.getStringOr("quest_id", ""))
                // The ID may be followed by "/<page>".
                .flatMap(id -> QuestObjectBase.parseHexId(id.split("/")[0]))
                .flatMap(id -> {
                    QuestObject target = ClientQuestFile.getInstance().get(id);
                    if (target instanceof Quest quest) {
                        return Optional.of(quest);
                    }
                    return target instanceof QuestLink questLink ? questLink.getQuest() : Optional.empty();
                })
                .map(quest -> quest.getCodeString());
    }

    /** Text without leftover {@code §} formatting codes. */
    private static String strip(String text) {
        String stripped = ChatFormatting.stripFormatting(text);
        return stripped == null ? "" : stripped;
    }
}
