package com.nexuscraft.nexuscuisine;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Lazily degrades a cooked dish's real nutrition/saturation over real elapsed time -- deliberately
 * check-on-read (see {@link FreshnessCalculator}'s own class comment): nothing schedules a
 * per-item ticking task, {@code CuisineSpoilageListener} just calls {@link #refresh} the next time
 * a tracked dish is actually interacted with (picked up, clicked in an inventory, about to be
 * eaten), and this class recomputes its current state from scratch off the item's own PDC tags.
 *
 * <p>Recomputing from {@link DishComponentFactory}'s stored quality-tiered baseline (never the
 * live, already-decayed value) makes this idempotent -- calling it twice in a row for the same
 * real elapsed time lands on the exact same numbers both times, so there's no risk of a dish
 * getting double-penalized just because a player opened their inventory twice.
 */
final class CuisineSpoilageService {

    private final CuisineConfig config;
    private final DishComponentFactory components;

    CuisineSpoilageService(CuisineConfig config, DishComponentFactory components) {
        this.config = config;
        this.components = components;
    }

    /** {@code false} when {@code item} isn't a dish this plugin tracks at all -- an ordinary
     *  vanilla food item, or a real dish that predates this plugin ever tagging it. */
    boolean isTracked(ItemStack item) {
        return components.dishIdOf(item) != null && components.cookedAtOf(item) != null;
    }

    SpoilageStage stageOf(ItemStack item, long nowMillis) {
        Long cookedAt = components.cookedAtOf(item);
        if (cookedAt == null) {
            return SpoilageStage.FRESH;
        }
        return SpoilageStage.fromFreshness(freshnessOf(item, cookedAt, nowMillis));
    }

    /**
     * Recomputes this exact item's real nutrition/saturation for its current real age and writes
     * them back via {@code ItemMeta#setFood}. A no-op for anything {@link #isTracked} says isn't
     * a dish this plugin owns. Once a dish reaches {@link SpoilageStage#SPOILED} it also carries a
     * real, guaranteed Hunger penalty from then on -- checked for first so repeated calls don't
     * stack a second copy of it.
     */
    void refresh(ItemStack item, long nowMillis) {
        if (!isTracked(item)) {
            return;
        }
        Integer baseNutrition = components.baseNutritionOf(item);
        Double baseSaturation = components.baseSaturationOf(item);
        if (baseNutrition == null || baseSaturation == null) {
            return;
        }

        long cookedAt = components.cookedAtOf(item);
        double freshness = freshnessOf(item, cookedAt, nowMillis);
        double retention = FreshnessCalculator.nutritionRetention(freshness);
        SpoilageStage stage = SpoilageStage.fromFreshness(freshness);

        ItemMeta meta = item.getItemMeta();
        FoodComponent food = meta.getFood();
        food.setNutrition(Math.max(0, (int) Math.round(baseNutrition * retention)));
        food.setSaturation((float) (baseSaturation * retention));

        if (stage == SpoilageStage.SPOILED && !hasSpoiledPenalty(food)) {
            food.addEffect(new PotionEffect(PotionEffectType.HUNGER, 260, 0), 1.0f);
        }

        meta.setFood(food);
        item.setItemMeta(meta);
    }

    boolean preservedAlready(ItemStack item) {
        return components.isPreserved(item);
    }

    /** Applies the real, craftable preservation method's effect -- see {@code
     *  DishComponentFactory#markPreserved}'s own comment. A no-op if {@code item} isn't a dish
     *  this plugin tracks; preservation only ever means something for a real, cooked dish. */
    boolean preserve(ItemStack item) {
        if (!isTracked(item)) {
            return false;
        }
        components.markPreserved(item);
        return true;
    }

    private double freshnessOf(ItemStack item, long cookedAtMillis, long nowMillis) {
        long windowMillis = config.spoilWindowMinutes * 60_000L;
        double multiplier = components.isPreserved(item) ? config.preservationMultiplier : 1.0;
        return FreshnessCalculator.freshnessFraction(cookedAtMillis, nowMillis, windowMillis, multiplier);
    }

    private static boolean hasSpoiledPenalty(FoodComponent food) {
        for (FoodComponent.FoodEffect effect : food.getEffects()) {
            if (effect.getEffect().getType() == PotionEffectType.HUNGER) {
                return true;
            }
        }
        return false;
    }
}
