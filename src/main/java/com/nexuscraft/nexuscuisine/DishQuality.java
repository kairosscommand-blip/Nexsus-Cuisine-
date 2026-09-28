package com.nexuscraft.nexuscuisine;

/**
 * A cooked dish's real, baked-in quality tier -- entirely this project's own invented ladder, same
 * "real vanilla has no concept of this at all" reasoning as this family's own {@code
 * TrackCondition} (NexusRails) and {@code GraveCondition} (NexusGrave), whose exact
 * javadoc-a-boundary-check style this mirrors. Unlike those two, a dish's tier isn't read off a
 * single continuous decay number -- {@link CookingSkill#rollQuality} weighs cook skill and
 * ingredient freshness into a weighted random pick across these five constants instead, since real
 * cooking has always had an element of chance a straight threshold can't capture.
 *
 * <p>Each tier's multipliers are applied directly onto a dish's real, per-item {@code
 * FoodComponent} the moment it's cooked ({@code DishComponentFactory}) -- a Masterwork stew and a
 * Burnt one are genuinely different real items, not the same item with a cosmetic label. There is
 * no eat-time multiplier here (an earlier version of this enum had one) -- see {@code
 * FoodComponent}'s own class comment for why: real eat-time isn't exposed through any real,
 * public Bukkit surface at this project's target paper-api version at all. {@link
 * #bonusEffectChance()} is still real; it's just rolled and applied at the moment of real
 * consumption now ({@code CuisineConsumptionListener}) rather than baked onto the item's own
 * {@code FoodComponent} at cook time, since that surface has no effects list to bake it onto here.
 */
public enum DishQuality {
    BURNT("§7Burnt", "scorched -- barely worth eating", 0.5, 0.4, 0.0),
    PLAIN("§fPlain", "cooked competently, nothing more", 1.0, 1.0, 0.0),
    HEARTY("§aHearty", "a real cut above -- filling and well made", 1.25, 1.2, 0.10),
    EXQUISITE("§bExquisite", "genuinely impressive craft", 1.5, 1.5, 0.30),
    MASTERWORK("§6§lMasterwork", "the kind of dish a village remembers", 1.85, 2.0, 0.60);

    private final String display;
    private final String description;
    private final double nutritionMultiplier;
    private final double saturationMultiplier;
    private final double bonusEffectChance;

    DishQuality(String display, String description, double nutritionMultiplier,
                double saturationMultiplier, double bonusEffectChance) {
        this.display = display;
        this.description = description;
        this.nutritionMultiplier = nutritionMultiplier;
        this.saturationMultiplier = saturationMultiplier;
        this.bonusEffectChance = bonusEffectChance;
    }

    public String display() {
        return display;
    }

    public String description() {
        return description;
    }

    /** Multiplier applied onto a {@link DishRecord#baseNutrition()} when {@code
     *  DishComponentFactory} builds this cook's real {@code FoodComponent}. */
    public double nutritionMultiplier() {
        return nutritionMultiplier;
    }

    public double saturationMultiplier() {
        return saturationMultiplier;
    }

    /** Real odds (0.0-1.0) this tier attaches a bonus effect at all, rolled fresh each time the
     *  dish is actually eaten (see {@code CuisineConsumptionListener}) -- BURNT and PLAIN never
     *  do; everything HEARTY and above has a real, increasing shot at one. */
    public double bonusEffectChance() {
        return bonusEffectChance;
    }
}
