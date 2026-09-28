package com.nexuscraft.nexuscuisine;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.Random;

/**
 * Turns a resolved {@link CookRecord} into a real, physical {@code ItemStack} -- the one place in
 * this plugin that actually calls {@code ItemMeta#setFood(FoodComponent)}, per that real method's
 * own "must be set to apply the changes" contract. Every quality-tiered dish this plugin ever
 * hands a player passes through here exactly once, at the moment it's cooked; nothing downstream
 * (the villager economy, the spoilage service) ever rebuilds a dish's {@code FoodComponent} from
 * scratch -- they only ever read the PDC tags this class writes and, for spoilage, scale the
 * nutrition/saturation this class already baked in.
 *
 * <p>The PDC tags below are this whole plugin's only source of truth for "what dish is this, how
 * good was the cook, when was it made, and what did it start at" -- {@code CuisineSpoilageService}
 * (freshness) and {@code /nexuscuisine inspect} (player-facing info) both read them back rather
 * than each plugin subsystem inventing its own tagging scheme. {@code baseNutritionKey}/{@code
 * baseSaturationKey} exist specifically so spoilage math is idempotent: {@code
 * CuisineSpoilageService} always recomputes retention from this quality-tiered original, never
 * from whatever the live, already-decayed {@code FoodComponent} currently reads, so calling it
 * twice in a row for the same real elapsed time never compounds a double penalty.
 */
final class DishComponentFactory {

    private final NamespacedKey dishIdKey;
    private final NamespacedKey qualityKey;
    private final NamespacedKey cookedAtKey;
    private final NamespacedKey baseNutritionKey;
    private final NamespacedKey baseSaturationKey;
    private final NamespacedKey preservedKey;

    DishComponentFactory(Plugin plugin) {
        this.dishIdKey = new NamespacedKey(plugin, "cuisine_dish_id");
        this.qualityKey = new NamespacedKey(plugin, "cuisine_quality");
        this.cookedAtKey = new NamespacedKey(plugin, "cuisine_cooked_at");
        this.baseNutritionKey = new NamespacedKey(plugin, "cuisine_base_nutrition");
        this.baseSaturationKey = new NamespacedKey(plugin, "cuisine_base_saturation");
        this.preservedKey = new NamespacedKey(plugin, "cuisine_preserved");
    }

    /**
     * Builds the real, physical dish item for a resolved {@link CookRecord}: a fresh {@code
     * ItemStack} of {@code dish.resultMaterial()}, with a real {@link FoodComponent} scaled by
     * {@code record.quality()}'s own multipliers, a chance-gated bonus effect for HEARTY-and-above
     * tiers (see {@link DishQuality#bonusEffectChance()}), and every PDC tag this class owns
     * already written on -- ready to hand straight to a player or drop in a furnace output slot.
     */
    ItemStack buildDish(DishRecord dish, CookRecord record, Random random) {
        ItemStack item = new ItemStack(dish.resultMaterial());
        ItemMeta meta = item.getItemMeta();

        DishQuality quality = record.quality();
        int qualityNutrition = Math.max(0, (int) Math.round(dish.baseNutrition() * quality.nutritionMultiplier()));
        double qualitySaturation = dish.baseSaturation() * quality.saturationMultiplier();

        FoodComponent food = meta.getFood();
        food.setNutrition(qualityNutrition);
        food.setSaturation((float) qualitySaturation);
        food.setEatSeconds((float) (dish.baseEatSeconds() * quality.eatSecondsMultiplier()));

        if (random.nextDouble() < quality.bonusEffectChance()) {
            PotionEffect bonus = new PotionEffect(dish.category().bonusEffectType(), 200, 0);
            food.addEffect(bonus, 1.0f);
        }
        meta.setFood(food);

        meta.getPersistentDataContainer().set(dishIdKey, PersistentDataType.STRING, dish.id());
        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, quality.name());
        meta.getPersistentDataContainer().set(cookedAtKey, PersistentDataType.LONG, record.cookedAtMillis());
        meta.getPersistentDataContainer().set(baseNutritionKey, PersistentDataType.INTEGER, qualityNutrition);
        meta.getPersistentDataContainer().set(baseSaturationKey, PersistentDataType.DOUBLE, qualitySaturation);

        item.setItemMeta(meta);
        return item;
    }

    /** Null when {@code item} was never tagged by {@link #buildDish} -- an ordinary, un-cooked
     *  vanilla food item, for instance. */
    String dishIdOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(dishIdKey, PersistentDataType.STRING);
    }

    DishQuality qualityOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(qualityKey, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return DishQuality.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Null when untagged (see {@link #dishIdOf}); otherwise the real epoch millisecond this
     *  specific item instance was cooked. */
    Long cookedAtOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(cookedAtKey, PersistentDataType.LONG);
    }

    /** The quality-tiered nutrition this exact item started at, before any real-time spoilage
     *  retention is applied -- see this class's own comment for why spoilage always recomputes
     *  from this rather than the live, possibly-already-decayed value. */
    Integer baseNutritionOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(baseNutritionKey, PersistentDataType.INTEGER);
    }

    Double baseSaturationOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(baseSaturationKey, PersistentDataType.DOUBLE);
    }

    boolean isPreserved(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }
        Boolean value = item.getItemMeta().getPersistentDataContainer().get(preservedKey, PersistentDataType.BOOLEAN);
        return value != null && value;
    }

    /** Real, craftable preservation method's actual effect: from this moment on, {@code
     *  CuisineSpoilageService} scales this item's real elapsed time by {@code
     *  CuisineConfig#preservationMultiplier} instead of 1.0, slowing (never fully stopping) its
     *  clock -- see {@code FreshnessCalculator#freshnessFraction}'s own comment on why it's
     *  deliberately a slowdown, not an outright pause. */
    void markPreserved(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(preservedKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
    }
}
