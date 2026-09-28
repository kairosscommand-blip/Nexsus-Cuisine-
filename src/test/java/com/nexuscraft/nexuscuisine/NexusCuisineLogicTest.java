package com.nexuscraft.nexuscuisine;

import org.bukkit.Material;

import java.util.Random;

/**
 * Standalone pure-logic test suite -- a plain {@code main} method, no JUnit, no Bukkit runtime.
 * Exercises every class in this plugin that has no real Bukkit-server dependency at all: the
 * cooking skill XP/level curve and quality roll, the freshness/spoilage math, the food scarcity
 * index, and the dish catalog. Same convention as every other sibling in this family.
 */
public final class NexusCuisineLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testSkillCurve();
        testQualityRollSkillShift();
        testQualityRollFreshnessShift();
        testXpForDishOrdering();
        testFreshnessCalculator();
        testSpoilageStageBoundaries();
        testScarcityIndexClampAndDecay();
        testDishCatalogLookups();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testSkillCurve() {
        CuisineConfig config = new CuisineConfig(null);

        expect(CookingSkill.levelFor(0, config) == 1, "level 1 at zero XP");
        long level5Xp = CookingSkill.xpForLevel(5, config);
        expect(CookingSkill.levelFor(level5Xp, config) == 5, "exact level-5 XP lands on level 5");
        expect(CookingSkill.levelFor(level5Xp - 1, config) == 4, "one XP short of level 5 stays level 4");

        int maxLevel = config.skillMaxLevel;
        long hugeXp = CookingSkill.xpForLevel(maxLevel, config) + 1_000_000L;
        expect(CookingSkill.levelFor(hugeXp, config) == maxLevel, "level never exceeds configured max");
        expect(CookingSkill.xpForNextLevel(maxLevel, config) == -1, "no next level once at max");

        long into = CookingSkill.xpIntoCurrentLevel(level5Xp + 7, 5, config);
        expect(into == 7, "xpIntoCurrentLevel measures progress past the level floor");
    }

    private static void testQualityRollSkillShift() {
        CuisineConfig config = new CuisineConfig(null);
        Random random = new Random(42);

        int trials = 20_000;
        int masterworkAtLevel1 = 0;
        int masterworkAtHighLevel = 0;
        for (int i = 0; i < trials; i++) {
            if (CookingSkill.rollQuality(1, 0.5, random, config) == DishQuality.MASTERWORK) {
                masterworkAtLevel1++;
            }
            if (CookingSkill.rollQuality(config.skillMaxLevel, 0.5, random, config) == DishQuality.MASTERWORK) {
                masterworkAtHighLevel++;
            }
        }
        expect(masterworkAtHighLevel > masterworkAtLevel1,
                "a max-level cook rolls Masterwork far more often than a level-1 cook ("
                        + masterworkAtHighLevel + " vs " + masterworkAtLevel1 + ")");
    }

    private static void testQualityRollFreshnessShift() {
        CuisineConfig config = new CuisineConfig(null);
        Random random = new Random(7);

        int trials = 20_000;
        int burntWithFreshIngredient = 0;
        int burntWithStaleIngredient = 0;
        for (int i = 0; i < trials; i++) {
            if (CookingSkill.rollQuality(10, 1.0, random, config) == DishQuality.BURNT) {
                burntWithFreshIngredient++;
            }
            if (CookingSkill.rollQuality(10, 0.0, random, config) == DishQuality.BURNT) {
                burntWithStaleIngredient++;
            }
        }
        expect(burntWithStaleIngredient > burntWithFreshIngredient,
                "a stale ingredient burns far more often than a fresh one at the same skill level ("
                        + burntWithStaleIngredient + " vs " + burntWithFreshIngredient + ")");
    }

    private static void testXpForDishOrdering() {
        long previous = -1;
        for (DishQuality quality : DishQuality.values()) {
            long xp = CookingSkill.xpForDish(quality);
            expect(xp > previous, quality + " awards strictly more XP than the tier before it");
            previous = xp;
        }
    }

    private static void testFreshnessCalculator() {
        long now = 1_000_000L;
        long windowMillis = 100_000L;

        expect(FreshnessCalculator.freshnessFraction(now, now, windowMillis, 1.0) == 1.0,
                "freshness is 1.0 the instant something is tagged");
        expect(FreshnessCalculator.freshnessFraction(now - windowMillis, now, windowMillis, 1.0) == 0.0,
                "freshness hits exactly 0.0 at the end of the window");
        expect(FreshnessCalculator.freshnessFraction(now - (2 * windowMillis), now, windowMillis, 1.0) == 0.0,
                "freshness never goes negative past the window");

        double halfway = FreshnessCalculator.freshnessFraction(now - (windowMillis / 2), now, windowMillis, 1.0);
        expect(Math.abs(halfway - 0.5) < 0.0001, "freshness falls off linearly");

        double preserved = FreshnessCalculator.freshnessFraction(now - windowMillis, now, windowMillis, 0.25);
        expect(preserved > 0.7, "a preserved item ages much slower than an unpreserved one (was " + preserved + ")");

        expect(FreshnessCalculator.nutritionRetention(1.0) == 1.0, "full freshness retains full nutrition");
        expect(FreshnessCalculator.nutritionRetention(0.0) > 0.0, "even fully spoiled retains a nonzero floor");
    }

    private static void testSpoilageStageBoundaries() {
        expect(SpoilageStage.fromFreshness(1.0) == SpoilageStage.FRESH, "1.0 freshness is FRESH");
        expect(SpoilageStage.fromFreshness(0.51) == SpoilageStage.FRESH, "just above the midpoint is still FRESH");
        expect(SpoilageStage.fromFreshness(0.5) == SpoilageStage.STALE, "the midpoint itself is STALE");
        expect(SpoilageStage.fromFreshness(0.01) == SpoilageStage.STALE, "just above zero is STALE");
        expect(SpoilageStage.fromFreshness(0.0) == SpoilageStage.SPOILED, "0.0 freshness is SPOILED");
    }

    private static void testScarcityIndexClampAndDecay() {
        CuisineConfig config = new CuisineConfig(null);
        FoodScarcityIndex index = new FoodScarcityIndex();

        expect(index.get(DishCategory.MEAT) == 1.0, "a fresh category starts at baseline");

        for (int i = 0; i < 1000; i++) {
            index.recordConsumption(DishCategory.MEAT, config);
        }
        expect(index.get(DishCategory.MEAT) <= config.economyMaxIndex,
                "heavy consumption never pushes the index past the configured max");

        for (int i = 0; i < 1000; i++) {
            index.recordSupply(DishCategory.MEAT, config);
        }
        expect(index.get(DishCategory.MEAT) >= config.economyMinIndex,
                "heavy supply never pushes the index below the configured min");

        index.recordConsumption(DishCategory.SEAFOOD, config);
        double beforeDecay = index.get(DishCategory.SEAFOOD);
        index.decayAll(config);
        double afterDecay = index.get(DishCategory.SEAFOOD);
        expect(afterDecay < beforeDecay && afterDecay >= 1.0,
                "decay eases a scarce category back down toward baseline without overshooting");
    }

    private static void testDishCatalogLookups() {
        expect(DishCatalog.byResultMaterial(Material.COOKED_BEEF) != null, "cooked beef is a tracked dish");
        expect(DishCatalog.byResultMaterial(Material.STONE) == null, "stone is not a tracked dish");
        expect(DishCatalog.isRawIngredient(Material.BEEF), "raw beef is a tracked raw ingredient");
        expect(!DishCatalog.isRawIngredient(Material.COOKED_BEEF), "cooked beef is not itself a raw ingredient");

        DishRecord byId = DishCatalog.byId("cooked-beef");
        expect(byId != null && byId.resultMaterial() == Material.COOKED_BEEF, "lookup by id matches lookup by material");
        expect(DishCatalog.byId("not-a-real-dish") == null, "an unknown id returns null rather than throwing");
    }

    private static void expect(boolean condition, String description) {
        if (condition) {
            passed++;
            System.out.println("PASS: " + description);
        } else {
            failed++;
            System.out.println("FAIL: " + description);
        }
    }
}
