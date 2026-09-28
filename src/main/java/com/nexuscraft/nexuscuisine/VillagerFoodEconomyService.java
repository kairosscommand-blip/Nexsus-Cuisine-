package com.nexuscraft.nexuscuisine;

import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.MerchantRecipe;

import java.util.HashMap;
import java.util.Map;

/**
 * Reuses vanilla's own real {@code MerchantRecipe#demand}/{@code specialPrice} price-adjustment
 * fields to make a real Farmer/Butcher/Fisherman villager's food-adjacent trades actually respond
 * to {@link FoodScarcityIndex} -- no custom shop, no custom GUI, just vanilla's own real trading
 * screen quietly reflecting a real, server-wide supply-and-demand number this plugin tracks.
 *
 * <p>Only FARMER/BUTCHER/FISHERMAN are wired here, not the full farmer/butcher/fisherman/shepherd
 * set originally floated -- checked against real vanilla trades first: a real Shepherd trades
 * wool, dye, and (depending on version) a saddle or painting, none of them food, so tying a food
 * scarcity index to Shepherd trades would have been this plugin inventing a connection real
 * vanilla doesn't have. A deliberate, documented scope trim (see CHANGES.md), not an oversight.
 *
 * <p>Keyed by a plain {@code HashMap<Villager.Profession, ...>}, never an {@code EnumMap} or a
 * {@code switch} over {@code Profession} -- real Bukkit's {@code Villager.Profession} is NOT
 * actually a Java enum (implements {@code OldEnum}/{@code Keyed} instead, so the JVM's real enum
 * machinery a {@code switch} or {@code EnumMap} would compile down to doesn't apply to it), a real
 * build failure this family has already hit once before (see {@code Villager.java}'s own comment,
 * and NexusMerchants' {@code ProfessionTradeCatalog} making the exact same choice for the exact
 * same reason).
 */
final class VillagerFoodEconomyService {

    private static final Map<Villager.Profession, DishCategory> PROFESSION_CATEGORY = new HashMap<>();

    static {
        PROFESSION_CATEGORY.put(Villager.Profession.FARMER, DishCategory.PRODUCE);
        PROFESSION_CATEGORY.put(Villager.Profession.BUTCHER, DishCategory.MEAT);
        PROFESSION_CATEGORY.put(Villager.Profession.FISHERMAN, DishCategory.SEAFOOD);
    }

    private final FoodScarcityIndex scarcity = new FoodScarcityIndex();
    private final CuisineConfig config;

    VillagerFoodEconomyService(CuisineConfig config) {
        this.config = config;
    }

    /** Null when this villager's profession isn't one the food economy touches at all (see this
     *  class's own comment on why Shepherd -- and every non-food profession -- is left out). */
    static DishCategory categoryFor(Villager.Profession profession) {
        return PROFESSION_CATEGORY.get(profession);
    }

    void recordConsumption(DishCategory category) {
        scarcity.recordConsumption(category, config);
    }

    void recordSupply(DishCategory category) {
        scarcity.recordSupply(category, config);
    }

    /** Called on a real, scheduled interval (see the main plugin's own repeating task) -- eases
     *  every category back toward baseline. */
    void decay() {
        scarcity.decayAll(config);
    }

    double indexOf(DishCategory category) {
        return scarcity.get(category);
    }

    /**
     * Real Bukkit event handler logic for {@code VillagerAcquireTradeEvent}: if {@code villager}
     * is a real, food-adjacent-profession {@code Villager} (not a {@code WanderingTrader}, which
     * real Bukkit also routes through this same event via the shared {@code AbstractVillager}
     * supertype -- see that event's own real, confirmed shape), nudges the newly-acquired trade's
     * real {@code specialPrice}/{@code demand} to reflect that category's current scarcity index.
     * A no-op, real trade left completely untouched, for anything else.
     */
    void onTradeAcquired(VillagerAcquireTradeEvent event) {
        AbstractVillager villager = event.getEntity();
        if (!(villager instanceof Villager real)) {
            return;
        }
        DishCategory category = categoryFor(real.getProfession());
        if (category == null) {
            return;
        }

        double index = indexOf(category);
        double deviation = index - 1.0;

        MerchantRecipe recipe = event.getRecipe();
        int specialPrice = (int) Math.round(deviation * config.economySpecialPriceScale);
        recipe.setSpecialPrice(specialPrice);

        int demand = Math.max(0, (int) Math.round(deviation * config.economyDemandScale));
        recipe.setDemand(demand);

        event.setRecipe(recipe);
    }
}
