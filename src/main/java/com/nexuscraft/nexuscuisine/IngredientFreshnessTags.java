package com.nexuscraft.nexuscuisine;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Tags a raw ingredient with the real epoch millisecond it was actually harvested/acquired, so
 * {@code CuisineCookListener} can feed a real ingredient-freshness fraction into {@link
 * CookingSkill#rollQuality} instead of treating every ingredient as equally fresh. {@code
 * CuisineIngredientListener} (wired in the Bukkit-facing layer) calls {@link #tagIfAbsent}
 * whenever a player picks up or receives one of the raw materials {@link DishCatalog} cares about.
 *
 * <p>Deliberate design choice: an ingredient this plugin never saw get tagged (bought from another
 * plugin's shop, spawned in by an admin, carried over from before this plugin was installed) is
 * simply treated as freshly-harvested ({@link #freshnessOf} returns {@code 1.0}) rather than
 * penalized -- there's no real way to know its true age, and guessing low would unfairly punish a
 * cook for something entirely out of their control.
 */
final class IngredientFreshnessTags {

    private final NamespacedKey harvestedAtKey;

    IngredientFreshnessTags(Plugin plugin) {
        this.harvestedAtKey = new NamespacedKey(plugin, "cuisine_harvested_at");
    }

    /** No-op if this exact item instance is already tagged -- real Bukkit {@code ItemStack}s
     *  don't merge-stack across different PDC contents, so this only ever runs once per real
     *  harvested unit. */
    void tagIfAbsent(ItemStack item, long nowMillis) {
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(harvestedAtKey, PersistentDataType.LONG)) {
            return;
        }
        meta.getPersistentDataContainer().set(harvestedAtKey, PersistentDataType.LONG, nowMillis);
        item.setItemMeta(meta);
    }

    /** See class comment for why an untagged ingredient defaults to fully fresh rather than
     *  fully stale. */
    double freshnessOf(ItemStack item, long nowMillis, CuisineConfig config) {
        if (!item.hasItemMeta()) {
            return 1.0;
        }
        Long harvestedAt = item.getItemMeta().getPersistentDataContainer()
                .get(harvestedAtKey, PersistentDataType.LONG);
        if (harvestedAt == null) {
            return 1.0;
        }
        long windowMillis = config.spoilWindowMinutes * 60_000L;
        return FreshnessCalculator.freshnessFraction(harvestedAt, nowMillis, windowMillis, 1.0);
    }
}
