package de.jeff_media.chestsort.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods to store complex data types inside {@link PersistentDataContainer}.
 */
public final class PersistentDataHelper {

    private static final String SEPARATOR = Character.toString('\u001F'); // Unit Separator, unlikely to appear in commands

    private PersistentDataHelper() {
        // utility class
    }

    /**
     * Stores the provided list as a single string entry in the {@link PersistentDataContainer}.
     */
    public static void setStringList(@NotNull PersistentDataContainer container,
                                     @NotNull NamespacedKey key,
                                     List<String> values) {
        if (values == null || values.isEmpty()) {
            container.remove(key);
            return;
        }
        container.set(key, PersistentDataType.STRING, serialize(values));
    }

    /**
     * Reads a list that was previously stored via {@link #setStringList(PersistentDataContainer, NamespacedKey, List)}.
     */
    @NotNull
    public static List<String> getStringList(@NotNull PersistentDataContainer container,
                                             @NotNull NamespacedKey key) {
        String raw = container.get(key, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = raw.split(SEPARATOR, -1);
        List<String> result = new ArrayList<>(parts.length);
        Collections.addAll(result, parts);
        return result;
    }

    private static String serialize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(SEPARATOR, values);
    }
}
