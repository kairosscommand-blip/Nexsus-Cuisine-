package com.nexuscraft.nexuscuisine;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The two real, player-facing moments this plugin's freshness/economy loop actually closes on:
 * a tracked dish being eaten (spoilage is refreshed one last time right before it takes effect,
 * and the real consumption becomes a real, observable demand signal for {@link
 * VillagerFoodEconomyService}), and a raw ingredient this plugin cares about being picked up
 * (tagged with a real harvested-at timestamp so a later cook can roll its freshness).
 */
final class CuisineConsumptionListener implements Listener {

    private final DishComponentFactory components;
    private final CuisineSpoilageService spoilageService;
    private final VillagerFoodEconomyService economyService;
    private final IngredientFreshnessTags freshnessTags;

    CuisineConsumptionListener(DishComponentFactory components, CuisineSpoilageService spoilageService,
                               VillagerFoodEconomyService economyService, IngredientFreshnessTags freshnessTags) {
        this.components = components;
        this.spoilageService = spoilageService;
        this.economyService = economyService;
        this.freshnessTags = freshnessTags;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        String dishId = components.dishIdOf(item);
        if (dishId == null) {
            return;
        }

        spoilageService.refresh(item, System.currentTimeMillis());
        // Real Bukkit contract (see this stub's own comment on PlayerItemConsumeEvent): mutating
        // the returned ItemStack in place does nothing on a real server -- it must be handed back
        // through setItem() to actually apply.
        event.setItem(item);

        DishRecord dish = DishCatalog.byId(dishId);
        if (dish != null) {
            economyService.recordConsumption(dish.category());
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!DishCatalog.isRawIngredient(stack.getType())) {
            return;
        }
        freshnessTags.tagIfAbsent(stack, System.currentTimeMillis());
    }
}
