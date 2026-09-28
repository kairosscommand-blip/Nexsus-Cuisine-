package com.nexuscraft.nexuscuisine;

/**
 * The real-elapsed-time math behind every freshness/spoilage number in this plugin -- both a raw
 * ingredient's own freshness at the moment it's cooked (feeds {@link CookingSkill#rollQuality})
 * and a finished dish's own freshness afterward (feeds {@link SpoilageStage}). Deliberately
 * lazy/check-on-read: a real cooked-at (or harvested-at) epoch millisecond is PDC-tagged onto the
 * real {@code ItemStack} once, and this class is only ever asked "what's it worth right now" the
 * next time that same item is actually interacted with -- eaten, picked up, opened in an
 * inventory. Nothing in {@code CuisineSpoilageService} ticks over every food item in existence on
 * a schedule, the same efficiency discipline this family already applies elsewhere (NexusForge and
 * NexusGrave don't tick every object they track every server tick either, where avoidable).
 */
final class FreshnessCalculator {

    private FreshnessCalculator() {
    }

    /**
     * Linear falloff from 1.0 (just tagged) to 0.0 (fully spoiled) over {@code windowMillis} of
     * real elapsed time, clamped to that range. {@code preservationMultiplier} shrinks how much of
     * the real elapsed time actually counts (0.25 means the item ages four times slower than the
     * clock on the wall -- {@code CuisinePreservationService}'s real, craftable preservation
     * method applies exactly this), never speeds it up (a multiplier above 1.0 is clamped to 1.0
     * so a config typo can't make food rot faster than real time).
     */
    static double freshnessFraction(long taggedAtMillis, long nowMillis, long windowMillis,
                                     double preservationMultiplier) {
        if (windowMillis <= 0) {
            return 1.0;
        }
        double multiplier = Math.min(1.0, Math.max(0.0, preservationMultiplier));
        long rawElapsed = Math.max(0L, nowMillis - taggedAtMillis);
        double effectiveElapsed = rawElapsed * multiplier;
        double fraction = 1.0 - (effectiveElapsed / (double) windowMillis);
        return Math.min(1.0, Math.max(0.0, fraction));
    }

    static SpoilageStage stageFor(long taggedAtMillis, long nowMillis, long windowMillis,
                                   double preservationMultiplier) {
        return SpoilageStage.fromFreshness(
                freshnessFraction(taggedAtMillis, nowMillis, windowMillis, preservationMultiplier));
    }

    /** How much of a dish's original nutrition/saturation it still delivers at a given freshness
     *  fraction -- FRESH delivers everything, then it falls away toward a real, non-zero floor
     *  (a Spoiled dish is still technically food on a real server, just a bad one to eat) rather
     *  than ever hitting exactly zero, which would make it un-eat-able instead of just unwise. */
    static double nutritionRetention(double freshnessFraction) {
        double floor = 0.15;
        return floor + (1.0 - floor) * freshnessFraction;
    }
}
