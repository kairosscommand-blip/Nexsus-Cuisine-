package com.nexuscraft.nexuscuisine;

import java.util.EnumMap;
import java.util.Map;

/**
 * A real, server-wide, slowly-moving supply-and-demand number per {@link DishCategory} -- 1.0 is
 * balanced, above 1.0 is real scarcity (that category is being eaten faster than it's being
 * cooked), below 1.0 is real surplus. {@code VillagerFoodEconomyService} reads this to drive
 * vanilla's own real {@code MerchantRecipe#setSpecialPrice}/{@code setDemand} on a real Farmer/
 * Butcher/Fisherman villager's food-adjacent trades -- something genuinely separate from vanilla's
 * own per-trade demand counter, same "a second, slower-moving, plugin-owned number layered over
 * vanilla's own" shape as NexusMerchants' own {@code PriceMemory}.
 *
 * <p>Deliberately in-memory only for this version, not PDC/disk-persisted -- unlike {@code
 * PriceMemory} (which is naturally per-villager and belongs on that villager's own PDC), this
 * index is server-wide and has no single natural real object to live on; a restart simply resets
 * every category back to baseline, a documented, deliberate v1 scope choice rather than a gap
 * (see CHANGES.md).
 */
final class FoodScarcityIndex {

    private final Map<DishCategory, Double> index = new EnumMap<>(DishCategory.class);

    double get(DishCategory category) {
        return index.getOrDefault(category, 1.0);
    }

    /** Called whenever a player's real consumption of a dish in this category resolves -- pushes
     *  this category's index up (toward scarcity), clamped to {@code config.economyMaxIndex}. */
    void recordConsumption(DishCategory category, CuisineConfig config) {
        double next = Math.min(config.economyMaxIndex, get(category) + config.economyConsumptionBump);
        index.put(category, next);
    }

    /** Called whenever a player's real cook of a dish in this category resolves -- pulls this
     *  category's index down (toward surplus), clamped to {@code config.economyMinIndex}. */
    void recordSupply(DishCategory category, CuisineConfig config) {
        double next = Math.max(config.economyMinIndex, get(category) - config.economySupplyBump);
        index.put(category, next);
    }

    /** Called on a real, scheduled interval for every category -- eases each one back toward the
     *  1.0 baseline by a fixed step, same shape as {@code PriceMemory#decayAll}, and drops an
     *  entry once it's close enough to baseline to just count as it. */
    void decayAll(CuisineConfig config) {
        index.replaceAll((category, value) -> {
            if (value > 1.0) {
                return Math.max(1.0, value - config.economyDecayPerInterval);
            }
            if (value < 1.0) {
                return Math.min(1.0, value + config.economyDecayPerInterval);
            }
            return value;
        });
        index.values().removeIf(value -> Math.abs(value - 1.0) < 0.001);
    }
}
