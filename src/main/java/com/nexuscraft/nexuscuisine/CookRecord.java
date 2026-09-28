package com.nexuscraft.nexuscuisine;

import java.util.UUID;

/**
 * One resolved cooking outcome -- the pure result of {@code CuisineCookListener} handing a
 * furnace/campfire completion off to {@link CookingSkill#rollQuality}, before any of it gets
 * written onto a real {@code ItemStack}'s {@code FoodComponent} or a real PDC tag. Kept separate
 * from that Bukkit-facing step so the roll itself stays fully unit-testable -- {@code
 * DishComponentFactory} (the Bukkit-facing consumer) is the only thing that ever turns one of
 * these into a real item.
 */
record CookRecord(
        UUID cookId,
        String dishId,
        DishQuality quality,
        long cookedAtMillis,
        long xpAwarded
) {
}
