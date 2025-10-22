package com.jeff_media.jefflib;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplified replacement for the original JeffLib {@code TextUtils}.
 * Supports legacy color codes (&amp;) and hex color tags formatted as {@code <#RRGGBB>}.
 */
public final class TextUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern HEX_RESET_PATTERN = Pattern.compile("<#/([A-Fa-f0-9]{6})>");

    private TextUtils() {
        // utility class
    }

    public static String format(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String withHex = replaceHexColors(text);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    private static String replaceHexColors(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            ChatColor color;
            try {
                color = ChatColor.of("#" + matcher.group(1));
            } catch (IllegalArgumentException ex) {
                color = ChatColor.RESET;
            }
            matcher.appendReplacement(buffer, color.toString());
        }
        matcher.appendTail(buffer);
        String partiallyReplaced = buffer.toString();

        matcher = HEX_RESET_PATTERN.matcher(partiallyReplaced);
        buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.RESET.toString());
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }
}
