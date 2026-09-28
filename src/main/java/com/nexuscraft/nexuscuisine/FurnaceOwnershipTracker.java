package com.nexuscraft.nexuscuisine;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Real vanilla's {@code FurnaceSmeltEvent} carries no player at all -- smelting happens
 * autonomously inside a furnace block, with no notion of "who's cooking" the way a real cast or a
 * real trade always names a real player. Rather than silently attributing every smelt to nobody
 * (and so awarding no cooking XP to anyone, ever, for furnace cooking -- most of this plugin's own
 * pitch), this plugin uses the same real, honest approximation this kind of attribution problem
 * has always used on a real server: whichever player most recently opened that specific furnace's
 * inventory is treated as the one cooking in it, for a real, bounded window afterward.
 *
 * <p>Keyed by world name + block coordinates rather than a {@code Block} instance or a {@code
 * Location} instance directly -- neither overrides {@code equals}/{@code hashCode} in this stub
 * tree (see {@code Block.java}'s own comment on the same constraint for its inventory caches), and
 * a fresh {@code Block}/{@code Location} object is handed out per call on a real server too.
 */
final class FurnaceOwnershipTracker {

    private record Entry(UUID playerId, long atMillis) {
    }

    private final Map<String, Entry> lastOpenedBy = new HashMap<>();
    private final long windowMillis;

    FurnaceOwnershipTracker(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    void recordOpened(Block furnace, Player player, long nowMillis) {
        lastOpenedBy.put(keyOf(furnace), new Entry(player.getUniqueId(), nowMillis));
    }

    /** Null when nobody's opened this furnace recently enough to still count, or nobody ever has. */
    UUID cookFor(Block furnace, long nowMillis) {
        Entry entry = lastOpenedBy.get(keyOf(furnace));
        if (entry == null) {
            return null;
        }
        if (nowMillis - entry.atMillis() > windowMillis) {
            return null;
        }
        return entry.playerId();
    }

    private static String keyOf(Block block) {
        Location location = block.getLocation();
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        return worldName + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
