package com.nexuscraft.nexuscuisine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

/**
 * Optional, read-only bridge into a real, separately-installed NexusEconomy plugin's real shop
 * pricing -- present-if-available, exactly this family's established soft-integration convention
 * (compare NexusCraftersGuild's own Vault {@code EconomyBridge}), so a quality-tier dish can show
 * what it would actually fetch in NexusEconomy's own real shop right now when that plugin happens
 * to be installed, with zero effect on anything else NexusCuisine does when it isn't.
 *
 * <p>Unlike this family's usual soft integrations (which hook a sibling plugin's own dedicated,
 * publicly-declared API class, or a real, standard cross-plugin service like Vault's {@code
 * Economy}), NexusEconomy is a real, independently-built plugin with no such published API and no
 * Vault-style service registration -- its real {@code NexusEconomyPlugin} main class does expose a
 * real, public {@code getShopManager()} accessor, and its real {@code ShopManager} does expose a
 * real, public {@code sellableItemFor(Material)} lookup returning a real {@code ShopItem} record
 * with a real {@code sell()} accessor (confirmed by reading NexusEconomy's own source directly,
 * not guessed). Since NexusCuisine's own build has no compile-time dependency on NexusEconomy at
 * all (this plugin must stay standalone-buildable without it), every one of those real calls has
 * to go through plain {@code java.lang.reflect} instead of a real method reference -- and since
 * that source could always change shape in a future NexusEconomy version this bridge hasn't seen,
 * every reflective step is wrapped so any mismatch degrades to "integration unavailable right
 * now" rather than ever crashing NexusCuisine itself.
 */
final class NexusEconomyBridge {

    private static final String PLUGIN_NAME = "NexusEconomy";

    boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    /**
     * The real, live emerald/credit sell price NexusEconomy's own shop would currently pay for
     * one unit of {@code material} -- null when NexusEconomy isn't installed/enabled, doesn't
     * carry that material in its shop at all (a real, ordinary "not sellable here" case, same as
     * {@code ShopManager#sellableItemFor} returning null on a real server), or this bridge's
     * reflective call failed for any reason (a future NexusEconomy version renaming or removing
     * one of the real methods this class depends on, most likely).
     */
    Double liveSellPriceFor(Material material) {
        if (!isPresent()) {
            return null;
        }
        try {
            Plugin economyPlugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (economyPlugin == null) {
                return null;
            }
            Object shopManager = economyPlugin.getClass().getMethod("getShopManager").invoke(economyPlugin);
            if (shopManager == null) {
                return null;
            }
            Object shopItem = shopManager.getClass()
                    .getMethod("sellableItemFor", Material.class)
                    .invoke(shopManager, material);
            if (shopItem == null) {
                return null;
            }
            Object sellValue = shopItem.getClass().getMethod("sell").invoke(shopItem);
            return sellValue instanceof Double ? (Double) sellValue : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // Reflection failure (method renamed/removed in a NexusEconomy version this bridge
            // hasn't seen, an unexpected return shape, etc.) -- fail silently, exactly like every
            // other soft integration in this family. NexusCuisine's own quality/spoilage/skill
            // systems never depend on this succeeding.
            return null;
        }
    }
}
