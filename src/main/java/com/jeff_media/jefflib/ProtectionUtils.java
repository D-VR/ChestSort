package com.jeff_media.jefflib;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Minimal protection hook that emulates the behaviour required by ChestSort.
 * Fires a {@link BlockBreakEvent} to ask other plugins whether the player may interact with the block.
 */
public final class ProtectionUtils {

    private ProtectionUtils() {
        // utility class
    }

    public static boolean canBreak(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        Block block = location.getBlock();
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        boolean allowed = !event.isCancelled();
        event.setCancelled(true);
        return allowed;
    }
}
