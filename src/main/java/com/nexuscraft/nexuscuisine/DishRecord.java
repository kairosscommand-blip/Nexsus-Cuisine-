package com.nexuscraft.nexuscuisine;

import org.bukkit.Material;

/**
 * One entry in {@link DishCatalog} -- a real vanilla food {@link Material} paired with this
 * project's own invented baseline nutrition/saturation/eat-time and its {@link DishCategory} for
 * economy scarcity tracking. Real vanilla already fixes a flat nutrition/saturation per food
 * material; the values here are this project's own deliberately-chosen baseline that {@link
 * DishQuality}'s multipliers are then applied on top of when {@code DishComponentFactory} builds
 * a specific cooked instance's real {@code FoodComponent} -- so a Plain-tier Cooked Beef still
 * lands close to vanilla's own real numbers, while a Masterwork one is a genuine step up.
 */
record DishRecord(
        String id,
        String displayName,
        Material resultMaterial,
        DishCategory category,
        int baseNutrition,
        float baseSaturation,
        float baseEatSeconds
) {
}
