package com.nexuscraft.nexuscuisine;

import java.util.Random;

/**
 * The math behind a player's cooking mastery -- levels up from cooking, shifting the odds of a
 * good {@link DishQuality} roll toward the better tiers the higher it climbs, exactly as pitched.
 * XP required for level {@code n} follows {@code xpBase * n^1.5}, rounded -- the same fixed shape
 * (steeper diminishing returns than a flat per-level cost) NexusAngler's own {@code AnglerSkill}
 * already uses for fishing mastery, reused deliberately rather than inventing a second curve shape
 * this family would then have to maintain two of. Total XP is what's actually persisted ({@code
 * CookingSkillStore}); level is always derived from it here rather than stored redundantly, so the
 * two can never drift out of sync with each other.
 */
final class CookingSkill {

    private CookingSkill() {
    }

    static long xpForLevel(int level, CuisineConfig config) {
        return Math.round(config.skillXpBase * Math.pow(level, 1.5));
    }

    static int levelFor(long totalXp, CuisineConfig config) {
        int level = 1;
        while (level < config.skillMaxLevel && totalXp >= xpForLevel(level + 1, config)) {
            level++;
        }
        return level;
    }

    static long xpIntoCurrentLevel(long totalXp, int level, CuisineConfig config) {
        return totalXp - xpForLevel(level, config);
    }

    static long xpForNextLevel(int level, CuisineConfig config) {
        return level >= config.skillMaxLevel ? -1 : xpForLevel(level + 1, config);
    }

    /** Fixed XP award per resolved dish, by the quality it actually turned out -- game-balance
     *  data, same "fixed in code" convention as {@code FishCatalog}'s own {@code xpForCatch}. A
     *  Masterwork dish teaches a cook far more than a Burnt one, which is deliberate: skill grows
     *  fastest for cooks already doing well, exactly the kind of real-world "success compounds"
     *  curve a flat per-cook XP award would flatten out. */
    static long xpForDish(DishQuality quality) {
        return switch (quality) {
            case BURNT -> 1;
            case PLAIN -> 3;
            case HEARTY -> 6;
            case EXQUISITE -> 10;
            case MASTERWORK -> 18;
        };
    }

    /**
     * Weighted random pick across every {@link DishQuality} tier. {@code ingredientFreshness} is
     * {@link FreshnessCalculator}'s own 0.0(stale)-1.0(fresh) fraction for the raw ingredient that
     * went into this dish -- a fresh ingredient in skilled hands stacks the odds toward
     * HEARTY/EXQUISITE/MASTERWORK; a stale one in a novice's hands stacks them right back toward
     * BURNT/PLAIN, and the two inputs combine additively rather than either one alone being able to
     * guarantee an outcome, so even a max-level cook occasionally burns a meal made from spoiled
     * ingredients, and even a fresh-faced cook occasionally gets lucky with fresh produce.
     */
    static DishQuality rollQuality(int level, double ingredientFreshness, Random random, CuisineConfig config) {
        double[] weights = {
                config.qualityWeightBurnt,
                config.qualityWeightPlain,
                config.qualityWeightHearty,
                config.qualityWeightExquisite,
                config.qualityWeightMasterwork,
        };

        double skillShift = Math.max(0, level - 1) * config.qualitySkillShiftPerLevel;
        double freshnessShift = (Math.min(1.0, Math.max(0.0, ingredientFreshness)) - 0.5)
                * 2.0 * config.qualityFreshnessShiftMax;
        applyShift(weights, skillShift + freshnessShift);

        double total = 0.0;
        for (double weight : weights) {
            total += weight;
        }
        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        DishQuality[] tiers = DishQuality.values();
        for (int i = 0; i < tiers.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return tiers[i];
            }
        }
        return tiers[tiers.length - 1];
    }

    /** Moves {@code shift} units of weight from the low tiers (BURNT, PLAIN, indices 0-1) to the
     *  high tiers (HEARTY, EXQUISITE, MASTERWORK, indices 2-4), proportionally to each group's own
     *  current weight so a tier that's already tiny doesn't get pushed below a real floor. A
     *  negative {@code shift} runs the same move in reverse (stale ingredients dragging a skilled
     *  cook's odds back down). Every weight is clamped to a small positive floor so no tier's real
     *  odds can ever hit exactly zero -- even a max-level master occasionally burns dinner. */
    private static void applyShift(double[] weights, double shift) {
        double floor = 0.01;
        int[] from = shift >= 0 ? new int[] {0, 1} : new int[] {2, 3, 4};
        int[] to = shift >= 0 ? new int[] {2, 3, 4} : new int[] {0, 1};
        double magnitude = Math.abs(shift);

        double fromTotal = 0.0;
        for (int i : from) {
            fromTotal += weights[i];
        }
        if (fromTotal <= floor * from.length) {
            return;
        }
        double movable = Math.min(magnitude, fromTotal - floor * from.length);
        if (movable <= 0.0) {
            return;
        }

        double toTotal = 0.0;
        for (int i : to) {
            toTotal += weights[i];
        }

        for (int i : from) {
            weights[i] -= movable * (weights[i] / fromTotal);
        }
        for (int i : to) {
            double share = toTotal > 0.0 ? weights[i] / toTotal : 1.0 / to.length;
            weights[i] += movable * share;
        }
    }
}
