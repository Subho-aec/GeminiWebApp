package com.subho.medbot.util;

/**
 * Utility class with static helper methods for text processing throughout MedBot.
 *
 * These methods are "pure functions" — they take input, produce output, and have no side effects.
 * Making the constructor private and methods static follows the utility-class pattern
 * (same approach used by java.util.Collections, java.util.Arrays, org.apache.commons.lang3.StringUtils).
 */
public final class TextUtils {                                         // "final" prevents subclassing — utility classes should never be extended.

    private TextUtils() {}                                             // Private constructor prevents instantiation. You call TextUtils.stripMarkdown(text),
                                                                       // never "new TextUtils()". This is a standard Java pattern for utility classes.

    /**
     * Strips common Markdown formatting to produce clean plaintext.
     * Used before sending text to the Text-to-Speech engine, because TTS should
     * speak "Important note" not "asterisk asterisk Important note asterisk asterisk".
     *
     * Handles: bold (**text**), italic (*text*), headers (# text), links [text](url),
     * code blocks (```text```), inline code (`text`), and HTML tags.
     */
    public static String stripMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";

        String text = markdown;
        text = text.replaceAll("```[\\s\\S]*?```", "");                // Remove fenced code blocks entirely (TTS shouldn't read code)
        text = text.replaceAll("`([^`]+)`", "$1");                     // Inline code: `text` → text
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");    // Links: [text](url) → text
        text = text.replaceAll("#{1,6}\\s*", "");                      // Headers: ### Title → Title
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");             // Bold: **text** → text
        text = text.replaceAll("\\*(.+?)\\*", "$1");                   // Italic: *text* → text
        text = text.replaceAll("__(.+?)__", "$1");                     // Bold alt: __text__ → text
        text = text.replaceAll("_(.+?)_", "$1");                       // Italic alt: _text_ → text
        text = text.replaceAll("~~(.+?)~~", "$1");                     // Strikethrough: ~~text~~ → text
        text = text.replaceAll("<[^>]+>", "");                         // HTML tags: <br>, <b>text</b> → text
        text = text.replaceAll("[-*+]\\s+", "");                       // List markers: - item, * item → item
        text = text.replaceAll("\\d+\\.\\s+", "");                     // Numbered lists: 1. item → item
        text = text.replaceAll("\\n{2,}", "\n");                       // Collapse multiple blank lines
        return text.trim();
    }

    /**
     * Truncates text to a maximum length, appending "..." if truncated.
     * Used for log messages and cache key generation where we don't want
     * unbounded strings consuming memory.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Checks if a string is null, empty, or contains only whitespace.
     * A null-safe alternative to String.isBlank() that doesn't throw NullPointerException.
     */
    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
