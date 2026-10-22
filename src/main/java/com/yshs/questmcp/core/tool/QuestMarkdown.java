package com.yshs.questmcp.core.tool;

import com.yshs.questmcp.core.source.QuestBook.Quest;
import com.yshs.questmcp.core.source.RichText;
import com.yshs.questmcp.core.source.RichText.Image;
import com.yshs.questmcp.core.source.RichText.Inline;
import com.yshs.questmcp.core.source.RichText.LineBreak;
import com.yshs.questmcp.core.source.RichText.Paragraph;
import com.yshs.questmcp.core.source.RichText.QuestLink;
import com.yshs.questmcp.core.source.RichText.Text;
import com.yshs.questmcp.core.source.RichText.UrlLink;
import java.util.Optional;

/** Wording shared by the quest tools. */
final class QuestMarkdown {
    private QuestMarkdown() {
    }

    /**
     * Explains when hidden chapters and quests are included.
     * @param out destination Markdown
     * @param book current visibility-filtered view
     */
    static void appendNotes(StringBuilder out, QuestBookView book) {
        if (book.revealHidden()) {
            out.append("revealHiddenQuests is on: chapters and quests the player cannot see in the quest book are ")
                    .append("included and marked (hidden).\n");
        }
    }

    /**
     * Renders rich text as Markdown: paragraphs separated by blank lines, a quest link as
     * {@code [<target title>](quest:<id>)} (plain link text if the target may not be shown), a web link as
     * {@code [text](url)} and an image as {@code [image: <path>]}.
     * @param text readable rich text
     * @param book resolves visible quest-link targets
     * @return rendered Markdown
     */
    static String richText(RichText text, QuestBookView book) {
        StringBuilder out = new StringBuilder();
        for (Paragraph paragraph : text.paragraphs()) {
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            StringBuilder line = new StringBuilder();
            for (Inline inline : paragraph.content()) {
                if (inline instanceof Text plain) {
                    line.append(plain.text());
                } else if (inline instanceof LineBreak) {
                    out.append(line.toString().stripTrailing()).append('\n');
                    line.setLength(0);
                } else if (inline instanceof QuestLink link) {
                    Optional<Quest> target = book.quest(link.questId());
                    if (target.isPresent()) {
                        line.append('[').append(linkText(book.title(target.get()))).append("](quest:")
                                .append(target.get().id()).append(')');
                    } else {
                        line.append(link.text());
                    }
                } else if (inline instanceof UrlLink link) {
                    line.append('[').append(linkText(link.text().isBlank() ? link.url() : link.text())).append("](")
                            .append(destination(link.url())).append(')');
                } else if (inline instanceof Image image) {
                    line.append("[image: ").append(image.path()).append(']');
                }
            }
            out.append(line.toString().stripTrailing());
        }
        return out.toString();
    }

    /** Link text with the brackets that would end it early escaped. */
    private static String linkText(String text) {
        return text.strip().replace("[", "\\[").replace("]", "\\]");
    }

    /** A link destination, in angle brackets if it contains characters that would end it early. */
    private static String destination(String url) {
        if (url.matches("[^\\s()<>]*")) {
            return url;
        }
        return "<" + url.replace("<", "%3C").replace(">", "%3E") + ">";
    }
}
