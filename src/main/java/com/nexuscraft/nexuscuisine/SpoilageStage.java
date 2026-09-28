package com.nexuscraft.nexuscuisine;

/**
 * A cooked dish's real-time freshness ladder, 1.0 (just plated) down to 0.0 (spoiled) -- entirely
 * this project's own invented mechanic, same boundary-check style as {@link DishQuality} and this
 * family's own {@code TrackCondition}/{@code GraveCondition}. Lazy/check-on-read by design (see
 * {@link FreshnessCalculator}'s own comment): nothing ticks every cooked item in existence every
 * server tick, this stage is only ever computed the moment a dish is next interacted with.
 */
public enum SpoilageStage {
    FRESH("§aFresh", "still tastes like it just left the pot"),
    STALE("§eStale", "past its best -- nutrition is fading"),
    SPOILED("§cSpoiled", "gone bad -- eating this now does more harm than good");

    private final String display;
    private final String description;

    SpoilageStage(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public String display() {
        return display;
    }

    public String description() {
        return description;
    }

    /** {@code freshnessFraction} is {@link FreshnessCalculator}'s own 1.0-to-0.0 output. */
    public static SpoilageStage fromFreshness(double freshnessFraction) {
        if (freshnessFraction <= 0.0) {
            return SPOILED;
        } else if (freshnessFraction <= 0.5) {
            return STALE;
        }
        return FRESH;
    }
}
