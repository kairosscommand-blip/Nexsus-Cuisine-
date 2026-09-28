package com.nexuscraft.nexuscuisine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CampfireStartEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a real, completed furnace/blast-furnace/smoker smelt into a quality-tiered dish, and
 * attributes it to a real player via {@link FurnaceOwnershipTracker} -- see that class's own
 * comment for why real vanilla's own {@code FurnaceSmeltEvent} needs that at all (it carries no
 * player reference on a real server).
 *
 * <p>Campfire cooking is deliberately NOT given the same full quality-roll treatment: real
 * vanilla has no completion event for it the way {@link FurnaceSmeltEvent} exists for a furnace
 * (see {@link CampfireStartEvent}'s own comment -- it only fires at the START of cooking, and
 * exposes no settable result to rewrite once it finishes). Reliably intercepting a campfire's real
 * finished item would need this stub tree to model a real {@code Campfire} {@code BlockState}'s
 * own recipe/inventory shape, which hasn't been confirmed against real javadoc -- so rather than
 * guess at that shape the way this family has been burned before, campfire dishes stay real,
 * ordinary vanilla food (still recognized and greeted here so the ownership tracker + a player
 * message both work, just without a quality roll). A documented, deliberate v1 scope trim, not an
 * oversight -- see CHANGES.md.
 */
final class CuisineCookListener implements Listener {

    private static final Set<Material> FURNACE_FAMILY =
            EnumSet.of(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER);
    private static final Set<Material> COOKING_BLOCKS = EnumSet.of(
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private final CookingSkillService skillService;
    private final VillagerFoodEconomyService economyService;
    private final FurnaceOwnershipTracker ownershipTracker;

    CuisineCookListener(CookingSkillService skillService, VillagerFoodEconomyService economyService,
                         FurnaceOwnershipTracker ownershipTracker) {
        this.skillService = skillService;
        this.economyService = economyService;
        this.ownershipTracker = ownershipTracker;
    }

    /** Records "this player is the one working this cooking block right now" -- the real, honest
     *  approximation {@link FurnaceOwnershipTracker}'s own comment explains. Only right-clicks on
     *  a real cooking-family block matter here; every other interaction is ignored. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!COOKING_BLOCKS.contains(block.getType())) {
            return;
        }
        ownershipTracker.recordOpened(block, event.getPlayer(), System.currentTimeMillis());
    }

    @EventHandler
    public void onSmelt(FurnaceSmeltEvent event) {
        if (!FURNACE_FAMILY.contains(event.getBlock().getType())) {
            return;
        }
        DishRecord dish = DishCatalog.byResultMaterial(event.getResult().getType());
        if (dish == null) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID cookId = ownershipTracker.cookFor(event.getBlock(), now);
        if (cookId == null) {
            // Nobody real Bukkit can attribute this smelt to recently enough -- an auto-hopper-fed
            // furnace nobody's stood at in a while, for instance. Leave vanilla's own result alone
            // rather than guess at a cook.
            return;
        }
        Player cook = Bukkit.getPlayer(cookId);
        if (cook == null) {
            // Real limitation: this plugin's own skill/freshness state lives on a real Player's
            // own PDC, which an offline OfflinePlayer doesn't expose in this stub tree (or, per
            // real Bukkit, isn't safely mutable off-thread for an offline player either) -- so a
            // cook who logged off mid-smelt simply doesn't get this specific dish's roll.
            return;
        }

        ItemStack source = event.getSource();
        ItemStack dishItem = skillService.resolveCook(cook, dish, source, now);
        event.setResult(dishItem);
        economyService.recordSupply(dish.category());
    }

    @EventHandler
    public void onCampfireStart(CampfireStartEvent event) {
        DishRecord dish = DishCatalog.byResultMaterial(event.getRecipe().getResult().getType());
        if (dish == null) {
            return;
        }
        UUID cookId = ownershipTracker.cookFor(event.getBlock(), System.currentTimeMillis());
        Player cook = cookId != null ? Bukkit.getPlayer(cookId) : null;
        if (cook != null) {
            cook.sendMessage("§7" + dish.displayName() + " is cooking over the campfire...");
        }
    }
}
