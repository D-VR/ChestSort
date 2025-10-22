package com.jeff_media.jefflib;

import de.jeff_media.chestsort.ChestSortPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Minimal subset of JeffLib's NBTAPI required by ChestSort.
 * Stores values using the Bukkit persistent data container.
 */
public final class NBTAPI {

    private NBTAPI() {
        // utility class
    }

    public static void addNBT(Player player, String key, String value) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(getKey(key), PersistentDataType.STRING, value);
    }

    public static void removeNBT(Player player, String key) {
        player.getPersistentDataContainer().remove(getKey(key));
    }

    public static String getNBT(Player player, String key, String defaultValue) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        String value = container.get(getKey(key), PersistentDataType.STRING);
        if (value != null) {
            return value;
        }
        if (defaultValue != null) {
            container.set(getKey(key), PersistentDataType.STRING, defaultValue);
        }
        return defaultValue;
    }

    private static NamespacedKey getKey(String key) {
        ChestSortPlugin plugin = ChestSortPlugin.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("ChestSort plugin instance is not available yet.");
        }
        return new NamespacedKey(plugin, key);
    }
}
