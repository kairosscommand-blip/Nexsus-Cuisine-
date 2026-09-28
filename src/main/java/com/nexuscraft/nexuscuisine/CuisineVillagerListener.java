package com.nexuscraft.nexuscuisine;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;

/** Thin real-event glue -- all the actual pricing logic lives in {@link
 *  VillagerFoodEconomyService#onTradeAcquired}, kept there so it stays testable independent of
 *  this listener even though {@code VillagerAcquireTradeEvent} itself has no pure-logic
 *  equivalent worth extracting. */
final class CuisineVillagerListener implements Listener {

    private final VillagerFoodEconomyService economyService;

    CuisineVillagerListener(VillagerFoodEconomyService economyService) {
        this.economyService = economyService;
    }

    @EventHandler
    public void onTradeAcquired(VillagerAcquireTradeEvent event) {
        economyService.onTradeAcquired(event);
    }
}
