package com.jeff_media.jefflib;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simplified replacement for the original JeffLib {@code TextUtils}.
 * Supports legacy color codes (&amp;) and hex color tags formatted as {@code <#RRGGBB>}.
 */
public final class TextUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern HEX_RESET_PATTERN = Pattern.compile("<#/([A-Fa-f0-9]{6})>");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>(.*?)<#/([A-Fa-f0-9]{6})>", Pattern.DOTALL);
    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String AMPERSAND_PLACEHOLDER = "{__CHESTSORT_AMP__}";
    private static final AtomicBoolean PLACEHOLDER_API_CHECKED = new AtomicBoolean(false);
    private static final AtomicBoolean PLACEHOLDER_API_AVAILABLE = new AtomicBoolean(false);
    private static final AtomicBoolean ITEMS_ADDERS_CHECKED = new AtomicBoolean(false);
    private static final AtomicBoolean ITEMS_ADDER_AVAILABLE = new AtomicBoolean(false);
    private static Method PLACEHOLDER_API_METHOD;
    private static Method ITEMS_ADDER_METHOD;

    private TextUtils() {
        // utility class
    }

    public static String format(String text) {
        return format(text, null);
    }

    public static String format(String text, OfflinePlayer player) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String processed = applyItemsAdder(text);
        processed = applyPlaceholders(processed, player);
        processed = applyGradients(processed);
        processed = replaceHexColors(processed);
        processed = replaceAmpersandHex(processed);
        processed = processed.replace("&&", AMPERSAND_PLACEHOLDER);
        processed = ChatColor.translateAlternateColorCodes('&', processed);
        return processed.replace(AMPERSAND_PLACEHOLDER, "&");
    }

    private static String applyGradients(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String startHex = matcher.group(1);
            String content = matcher.group(2);
            String endHex = matcher.group(3);
            String replacement = buildGradient(content, startHex, endHex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        if (found) {
            return applyGradients(buffer.toString());
        }
        return buffer.toString();
    }

    private static String buildGradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        char[] chars = text.toCharArray();
        int visibleChars = countVisibleCharacters(chars);
        if (visibleChars == 0) {
            return text;
        }

        int[] start = hexToRgb(startHex);
        int[] end = hexToRgb(endHex);

        StringBuilder result = new StringBuilder();
        int index = 0;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c == '&' && i + 1 < chars.length) {
                result.append(c).append(chars[++i]);
                continue;
            }

            double fraction = visibleChars == 1 ? 0 : (double) index / (visibleChars - 1);
            int r = interpolate(start[0], end[0], fraction);
            int g = interpolate(start[1], end[1], fraction);
            int b = interpolate(start[2], end[2], fraction);

            result.append(ChatColor.of(String.format("#%02X%02X%02X", r, g, b))).append(c);
            index++;
        }

        result.append(ChatColor.RESET);
        return result.toString();
    }

    private static int[] hexToRgb(String hex) {
        try {
            int color = Integer.parseInt(hex, 16);
            return new int[]{
                    (color >> 16) & 0xFF,
                    (color >> 8) & 0xFF,
                    color & 0xFF
            };
        } catch (NumberFormatException ex) {
            return new int[]{255, 255, 255};
        }
    }

    private static int interpolate(int start, int end, double fraction) {
        return (int) Math.round(start + (end - start) * fraction);
    }

    private static int countVisibleCharacters(char[] chars) {
        int count = 0;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '&' && i + 1 < chars.length) {
                i++; // skip formatting code
                continue;
            }
            count++;
        }
        return count;
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

    private static String replaceAmpersandHex(String input) {
        Matcher matcher = AMPERSAND_HEX_PATTERN.matcher(input);
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
        return buffer.toString();
    }

    private static String applyPlaceholders(String text, OfflinePlayer player) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!ensurePlaceholderApi()) {
            return text;
        }
        Method method = PLACEHOLDER_API_METHOD;
        if (method == null) {
            return text;
        }
        try {
            return (String) method.invoke(null, player, text);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return text;
        }
    }

    private static boolean ensurePlaceholderApi() {
        if (PLACEHOLDER_API_CHECKED.get()) {
            return PLACEHOLDER_API_AVAILABLE.get();
        }
        PLACEHOLDER_API_CHECKED.set(true);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            PLACEHOLDER_API_AVAILABLE.set(false);
            return false;
        }
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            PLACEHOLDER_API_METHOD = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            PLACEHOLDER_API_AVAILABLE.set(true);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            PLACEHOLDER_API_AVAILABLE.set(false);
        }
        return PLACEHOLDER_API_AVAILABLE.get();
    }

    private static String applyItemsAdder(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!ensureItemsAdder()) {
            return text;
        }
        Method method = ITEMS_ADDER_METHOD;
        if (method == null) {
            return text;
        }
        try {
            return (String) method.invoke(null, text);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return text;
        }
    }

    private static boolean ensureItemsAdder() {
        if (ITEMS_ADDERS_CHECKED.get()) {
            return ITEMS_ADDER_AVAILABLE.get();
        }
        ITEMS_ADDERS_CHECKED.set(true);
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            ITEMS_ADDER_AVAILABLE.set(false);
            return false;
        }
        try {
            Class<?> clazz = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            ITEMS_ADDER_METHOD = clazz.getMethod("replaceFontImages", String.class);
            ITEMS_ADDER_AVAILABLE.set(true);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            ITEMS_ADDER_AVAILABLE.set(false);
        }
        return ITEMS_ADDER_AVAILABLE.get();
    }
}
