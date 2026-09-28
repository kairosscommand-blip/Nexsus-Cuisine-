package com.nexuscraft.nexuscuisine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/** Parses config.yml's tunables. Same "config drives shared numbers, the catalog/tier data holds
 *  the fixed game-balance shape" split this whole family already uses (compare NexusAngler's own
 *  {@code AnglerConfig}, NexusMerchants' {@code MerchantsConfig}) -- {@link DishCatalog} and
 *  {@link DishQuality}/{@link SpoilageStage}'s own multipliers are the fixed half of that split;
 *  everything below is the tunable half. */
final class CuisineConfig {

    private final Plugin plugin;

    boolean enabled = true;

    // -- Cooking skill (CookingSkill) --
    int skillMaxLevel = 40;
    double skillXpBase = 10.0;

    // -- Quality roll (CookingSkill#rollQuality) --
    /** Base weight of each tier at skill level 1 with neutral (0.5) ingredient freshness --
     *  BURNT..MASTERWORK, same order as {@link DishQuality#values()}. A brand-new cook mostly
     *  turns out Plain food, occasionally burns it, and only rarely stumbles into something
     *  better. */
    double qualityWeightBurnt = 30.0;
    double qualityWeightPlain = 55.0;
    double qualityWeightHearty = 12.0;
    double qualityWeightExquisite = 2.7;
    double qualityWeightMasterwork = 0.3;
    /** Per level above 1, this much weight shifts off BURNT/PLAIN and onto HEARTY/EXQUISITE/
     *  MASTERWORK -- see {@code CookingSkill#rollQuality} for exactly how the shift is split. */
    double qualitySkillShiftPerLevel = 1.1;
    /** How much a full swing in ingredient freshness (0.0 stale to 1.0 fresh) can shift weight the
     *  same direction as skill -- stale ingredients can drag even a master cook's odds back down. */
    double qualityFreshnessShiftMax = 18.0;

    // -- Spoilage (FreshnessCalculator, CuisineSpoilageService) --
    /** Real minutes from freshly cooked (freshness 1.0) to fully spoiled (freshness 0.0). */
    int spoilWindowMinutes = 90;
    /** Multiplier real elapsed time is scaled by while a dish sits in real, craftable
     *  preservation packaging (see {@code CuisinePreservationListener}) -- 0.25 means it ages four
     *  times slower than the wall clock. */
    double preservationMultiplier = 0.25;

    // -- Furnace cook attribution (FurnaceOwnershipTracker) --
    /** How many real minutes a player who last opened a furnace/campfire is still credited as
     *  "the one cooking there" once it actually finishes -- see that class's own comment on why
     *  this approximation exists at all. */
    int furnaceOwnershipWindowMinutes = 10;

    // -- Villager food economy (FoodScarcityIndex, VillagerFoodEconomyService) --
    /** How much one real, resolved consumption of a category's dish bumps that category's
     *  scarcity index up (more eating without matching cooking drives real prices up). */
    double economyConsumptionBump = 0.015;
    /** How much one real, resolved cook of a category's dish pulls that category's scarcity index
     *  back down (real production easing real prices). */
    double economySupplyBump = 0.02;
    /** Per scheduled tick, how far every category's index drifts back toward the 1.0 baseline --
     *  same slow, always-easing-toward-neutral shape as NexusMerchants' own {@code PriceMemory}
     *  decay, just server-wide per category instead of per-villager per-trade. */
    double economyDecayPerInterval = 0.01;
    double economyMinIndex = 0.5;
    double economyMaxIndex = 2.0;
    /** How many emeralds of real {@code MerchantRecipe#setSpecialPrice} swing one full unit of
     *  scarcity-index deviation from baseline (1.0) is worth. */
    double economySpecialPriceScale = 3.0;
    /** How much real {@code MerchantRecipe#setDemand} a freshly-acquired trade starts pre-loaded
     *  with per unit of scarcity-index deviation above baseline -- surplus never pushes demand
     *  negative, only scarcity pre-loads it positive. */
    double economyDemandScale = 4.0;
    /** Real minutes between each scheduled {@code FoodScarcityIndex#decayAll} tick. */
    int economyDecayIntervalMinutes = 5;

    CuisineConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    void load(FileConfiguration c) {
        Logger log = plugin != null ? plugin.getLogger() : null;

        enabled = c.getBoolean("enabled", enabled);

        ConfigurationSection skill = c.getConfigurationSection("skill");
        if (skill != null) {
            skillMaxLevel = Math.max(1, skill.getInt("max-level", skillMaxLevel));
            skillXpBase = Math.max(1.0, skill.getDouble("xp-base", skillXpBase));
        }

        ConfigurationSection quality = c.getConfigurationSection("quality");
        if (quality != null) {
            qualityWeightBurnt = clampNonNegative(quality.getDouble("weight-burnt", qualityWeightBurnt));
            qualityWeightPlain = clampNonNegative(quality.getDouble("weight-plain", qualityWeightPlain));
            qualityWeightHearty = clampNonNegative(quality.getDouble("weight-hearty", qualityWeightHearty));
            qualityWeightExquisite = clampNonNegative(quality.getDouble("weight-exquisite", qualityWeightExquisite));
            qualityWeightMasterwork = clampNonNegative(quality.getDouble("weight-masterwork", qualityWeightMasterwork));
            qualitySkillShiftPerLevel = clampNonNegative(quality.getDouble("skill-shift-per-level", qualitySkillShiftPerLevel));
            qualityFreshnessShiftMax = clampNonNegative(quality.getDouble("freshness-shift-max", qualityFreshnessShiftMax));
        }

        ConfigurationSection spoilage = c.getConfigurationSection("spoilage");
        if (spoilage != null) {
            spoilWindowMinutes = Math.max(1, spoilage.getInt("window-minutes", spoilWindowMinutes));
            preservationMultiplier = Math.min(1.0, clampNonNegative(
                    spoilage.getDouble("preservation-multiplier", preservationMultiplier)));
        }

        ConfigurationSection furnace = c.getConfigurationSection("furnace-ownership");
        if (furnace != null) {
            furnaceOwnershipWindowMinutes = Math.max(1, furnace.getInt("window-minutes", furnaceOwnershipWindowMinutes));
        }

        ConfigurationSection economy = c.getConfigurationSection("economy");
        if (economy != null) {
            economyConsumptionBump = clampNonNegative(economy.getDouble("consumption-bump", economyConsumptionBump));
            economySupplyBump = clampNonNegative(economy.getDouble("supply-bump", economySupplyBump));
            economyDecayPerInterval = clampNonNegative(economy.getDouble("decay-per-interval", economyDecayPerInterval));
            economyMinIndex = Math.max(0.01, economy.getDouble("min-index", economyMinIndex));
            economyMaxIndex = Math.max(economyMinIndex, economy.getDouble("max-index", economyMaxIndex));
            economySpecialPriceScale = clampNonNegative(economy.getDouble("special-price-scale", economySpecialPriceScale));
            economyDemandScale = clampNonNegative(economy.getDouble("demand-scale", economyDemandScale));
            economyDecayIntervalMinutes = Math.max(1, economy.getInt("decay-interval-minutes", economyDecayIntervalMinutes));
        }

        if (!enabled && log != null) {
            log.info("NexusCuisine is disabled via config.yml.");
        }
    }

    private static double clampNonNegative(double value) {
        return Math.max(0.0, value);
    }
}
