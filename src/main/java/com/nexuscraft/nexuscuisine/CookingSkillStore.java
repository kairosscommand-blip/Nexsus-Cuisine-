package com.nexuscraft.nexuscuisine;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A player's total cooking XP, stored directly on their own real PDC ({@code Player} is a
 * PersistentDataHolder via {@code Entity}) -- survives a relog and a server restart with no extra
 * file to manage, same PDC-tagging convention this whole family already uses for per-player skill
 * data (compare NexusAngler's own {@code AnglerSkillStore}, whose exact shape this mirrors). Level
 * is never stored -- always derived from total XP via {@link CookingSkill#levelFor}, so the two
 * can't drift.
 */
final class CookingSkillStore {

    private final NamespacedKey xpKey;

    CookingSkillStore(Plugin plugin) {
        this.xpKey = new NamespacedKey(plugin, "cuisine_xp");
    }

    long xpOf(Player player) {
        Long value = player.getPersistentDataContainer().get(xpKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    int levelOf(Player player, CuisineConfig config) {
        return CookingSkill.levelFor(xpOf(player), config);
    }

    /** Returns the new total XP. */
    long addXp(Player player, long amount) {
        long updated = xpOf(player) + amount;
        player.getPersistentDataContainer().set(xpKey, PersistentDataType.LONG, updated);
        return updated;
    }
}
