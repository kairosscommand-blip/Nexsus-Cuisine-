package com.nexuscraft.nexuscuisine;

import org.bukkit.potion.PotionEffectType;

/**
 * The broad food family a {@link DishRecord} belongs to -- what {@code VillagerFoodEconomyService}
 * actually tracks scarcity/surplus against (a real Farmer villager's real trades only care about
 * {@code PRODUCE}/{@code BAKED} pressure, a Butcher's only about {@code MEAT}, and so on), and what
 * {@code CookingSkill}'s own per-category XP curve reads. Entirely this project's own invented
 * classification -- real vanilla has no such grouping -- same "an original taxonomy laid over a
 * small, reused set of real vanilla items" spirit as NexusAngler's own {@code CatchCategory}.
 */
public enum DishCategory {
    PRODUCE(PotionEffectType.REGENERATION),
    BAKED(PotionEffectType.SATURATION),
    MEAT(PotionEffectType.STRENGTH),
    SEAFOOD(PotionEffectType.WATER_BREATHING),
    SOUP(PotionEffectType.REGENERATION),
    SWEET(PotionEffectType.HASTE);

    private final PotionEffectType bonusEffectType;

    DishCategory(PotionEffectType bonusEffectType) {
        this.bonusEffectType = bonusEffectType;
    }

    /** Fixed, game-balance choice of which real vanilla effect a {@link DishQuality} bonus roll
     *  actually grants for a dish in this category -- e.g. a Masterwork stew (SOUP) can carry
     *  Regeneration, a Masterwork steak (MEAT) carries Strength. This project's own invented
     *  pairing, same "fixed in code" spirit as everything else in {@link DishCatalog}. */
    public PotionEffectType bonusEffectType() {
        return bonusEffectType;
    }
}
