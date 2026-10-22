package com.yshs.questmcp.core.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formatted text such as a quest description, reduced to what matters to a reader: plain text, line and paragraph
 * breaks, links to quests and web pages, and images. Styles, colours and formatting codes are not kept.
 *
 * <p>Build it with {@link #builder()}, which keeps the tree tidy: no empty paragraphs, no line break at the start or
 * end of a paragraph, adjacent text merged.
 *
 * @param paragraphs the paragraphs in order; none is empty
 */
public record RichText(List<Paragraph> paragraphs) {
    /** No text at all. */
    public static final RichText EMPTY = new RichText(List.of());

    public RichText {
        paragraphs = List.copyOf(paragraphs);
    }

    public boolean isEmpty() {
        return paragraphs.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A paragraph: inline content, possibly spread over several lines by {@link LineBreak}s.
     *
     * @param content the content in order; never empty, and never starting or ending with a line break
     */
    public record Paragraph(List<Inline> content) {
        public Paragraph {
            content = List.copyOf(content);
        }
    }

    /** A piece of a paragraph. */
    public sealed interface Inline permits Text, LineBreak, QuestLink, UrlLink, Image {
    }

    /** Plain text, without line breaks. */
    public record Text(String text) implements Inline {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /** The end of a line within a paragraph. */
    public record LineBreak() implements Inline {
    }

    /**
     * A link that opens another quest of the quest book.
     *
     * @param questId the ID of the quest it opens, as 16 upper-case hex digits
     * @param text    the link text as written in the description
     */
    public record QuestLink(String questId, String text) implements Inline {
        public QuestLink {
            Objects.requireNonNull(questId, "questId");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A link to a web page.
     *
     * @param url  the address it opens
     * @param text the link text
     */
    public record UrlLink(String url, String text) implements Inline {
        public UrlLink {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * An image, of which only the reference is kept.
     *
     * @param path the image as the quest book refers to it, e.g. {@code minecraft:textures/item/snowball.png}
     */
    public record Image(String path) implements Inline {
        public Image {
            Objects.requireNonNull(path, "path");
        }
    }

    /** Builds {@link RichText} piece by piece, in reading order. */
    public static final class Builder {
        private final List<Paragraph> paragraphs = new ArrayList<>();
        private final List<Inline> current = new ArrayList<>();

        private Builder() {
        }

        /** Appends plain text; each {@code \n} in it is a {@link #lineBreak()}. */
        public Builder text(String text) {
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    lineBreak();
                }
                if (!lines[i].isEmpty()) {
                    if (!current.isEmpty() && current.getLast() instanceof Text previous) {
                        current.set(current.size() - 1, new Text(previous.text() + lines[i]));
                    } else {
                        current.add(new Text(lines[i]));
                    }
                }
            }
            return this;
        }

        /** Appends a link to a quest; text right after a link to the same quest extends it. */
        public Builder questLink(String questId, String text) {
            String linkText = singleLine(text);
            if (!current.isEmpty() && current.getLast() instanceof QuestLink previous
                    && previous.questId().equals(questId)) {
                current.set(current.size() - 1, new QuestLink(questId, previous.text() + linkText));
            } else {
                current.add(new QuestLink(questId, linkText));
            }
            return this;
        }

        /** Appends a link to a web page; text right after a link to the same page extends it. */
        public Builder urlLink(String url, String text) {
            String linkText = singleLine(text);
            if (!current.isEmpty() && current.getLast() instanceof UrlLink previous && previous.url().equals(url)) {
                current.set(current.size() - 1, new UrlLink(url, previous.text() + linkText));
            } else {
                current.add(new UrlLink(url, linkText));
            }
            return this;
        }

        public Builder image(String path) {
            current.add(new Image(path));
            return this;
        }

        /**
         * Ends the current line. A line break at the start of a paragraph is dropped, and an empty (or blank) line ends
         * the paragraph.
         */
        public Builder lineBreak() {
            dropTrailingBlankText();
            if (current.isEmpty()) {
                return this;
            }
            if (current.getLast() instanceof LineBreak) {
                return paragraphBreak();
            }
            current.add(new LineBreak());
            return this;
        }

        /** Ends the current paragraph, if it has any content. */
        public Builder paragraphBreak() {
            dropTrailingBlankText();
            while (!current.isEmpty() && current.getLast() instanceof LineBreak) {
                current.removeLast();
                dropTrailingBlankText();
            }
            if (!current.isEmpty()) {
                paragraphs.add(new Paragraph(current));
            }
            current.clear();
            return this;
        }

        private void dropTrailingBlankText() {
            if (!current.isEmpty() && current.getLast() instanceof Text text && text.text().isBlank()) {
                current.removeLast();
            }
        }

        public RichText build() {
            paragraphBreak();
            return paragraphs.isEmpty() ? EMPTY : new RichText(paragraphs);
        }

        private static String singleLine(String text) {
            return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        }
    }
}
