package com.nexuscraft.nexuscuisine;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The real, craftable preservation method the design called for -- deliberately reuses a real,
 * ordinary vanilla item (paper) as the "wrapping" rather than inventing a new custom item, same
 * "lean on a real vanilla item already in a player's reach" spirit as {@code
 * FoodComponent#getUsingConvertsTo()}'s own mushroom-stew-to-bowl mechanic. A player sneaking and
 * right-clicking while holding a tracked dish, with at least one real paper anywhere in their
 * inventory, consumes that paper and marks the held dish preserved (see {@code
 * CuisineSpoilageService#preserve}) -- from then on it ages at {@code
 * CuisineConfig#preservationMultiplier} of the normal rate.
 *
 * <p>Scans and rewrites {@code PlayerInventory#getStorageContents()} by index directly rather
 * than calling {@code PlayerInventory#removeItem} -- this stub tree's own {@code removeItem} is a
 * deliberate no-op placeholder (see that class's own file), so consuming the paper this way is
 * what actually works against both this sandbox and a real server alike.
 */
final class CuisinePreservationListener implements Listener {

    private final CuisineSpoilageService spoilageService;

    CuisinePreservationListener(CuisineSpoilageService spoilageService) {
        this.spoilageService = spoilageService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || !spoilageService.isTracked(held)) {
            return;
        }

        Player player = event.getPlayer();
        if (spoilageService.preservedAlready(held)) {
            return;
        }
        if (!consumeOnePaper(player.getInventory())) {
            player.sendMessage("§cYou need a paper to wrap and preserve this dish.");
            return;
        }

        spoilageService.preserve(held);
        player.sendMessage("§aWrapped -- this dish will keep much longer now.");
    }

    private static boolean consumeOnePaper(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != Material.PAPER || stack.getAmount() <= 0) {
                continue;
            }
            if (stack.getAmount() == 1) {
                inventory.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                inventory.setItem(i, stack);
            }
            return true;
        }
        return false;
    }
}
