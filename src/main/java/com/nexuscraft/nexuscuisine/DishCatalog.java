package com.nexuscraft.nexuscuisine;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fixed dish table -- what {@code CuisineCookListener} looks a freshly-cooked item's real
 * {@link Material} up against, and what {@code VillagerFoodEconomyService} buckets its own
 * per-{@link DishCategory} scarcity index by. Same "game-balance data fixed in code, shared
 * numbers in config.yml" split this whole family already uses (compare NexusAngler's own {@code
 * FishCatalog}). Baseline nutrition/saturation/eat-time values below track real vanilla's own
 * numbers closely at PLAIN tier -- {@link DishQuality} is what actually makes a specific cooked
 * instance better or worse than vanilla, not this catalog.
 */
final class DishCatalog {

    private static final List<DishRecord> ALL = new ArrayList<>();
    private static final Map<Material, DishRecord> BY_MATERIAL = new HashMap<>();

    /** Raw materials {@code CuisineIngredientListener} tags with a real harvested-at timestamp on
     *  pickup -- fixed, game-balance data same as everything else in this catalog. Deliberately
     *  just the raw forms {@link #BY_MATERIAL}'s own dishes are actually cooked from, not every
     *  edible vanilla item -- an ingredient this plugin never turns into a tracked dish has no
     *  freshness roll to feed. */
    private static final Set<Material> RAW_INGREDIENTS = EnumSet.of(
            Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON, Material.RABBIT,
            Material.COD, Material.SALMON, Material.WHEAT, Material.POTATO, Material.CARROT,
            Material.BEETROOT, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.EGG);

    static {
        // --- MEAT (Butcher-adjacent) ---
        add(new DishRecord("cooked-beef", "Cooked Beef", Material.COOKED_BEEF,
                DishCategory.MEAT, 8, 12.8f, 1.6f));
        add(new DishRecord("cooked-porkchop", "Cooked Porkchop", Material.COOKED_PORKCHOP,
                DishCategory.MEAT, 8, 12.8f, 1.6f));
        add(new DishRecord("cooked-chicken", "Cooked Chicken", Material.COOKED_CHICKEN,
                DishCategory.MEAT, 6, 7.2f, 1.6f));
        add(new DishRecord("cooked-mutton", "Cooked Mutton", Material.COOKED_MUTTON,
                DishCategory.MEAT, 6, 9.6f, 1.6f));
        add(new DishRecord("cooked-rabbit", "Cooked Rabbit", Material.COOKED_RABBIT,
                DishCategory.MEAT, 5, 6.0f, 1.6f));

        // --- SEAFOOD (Fisherman-adjacent) ---
        add(new DishRecord("cooked-cod", "Cooked Cod", Material.COOKED_COD,
                DishCategory.SEAFOOD, 5, 6.0f, 1.6f));
        add(new DishRecord("cooked-salmon", "Cooked Salmon", Material.COOKED_SALMON,
                DishCategory.SEAFOOD, 6, 9.6f, 1.6f));

        // --- BAKED / PRODUCE (Farmer-adjacent) ---
        add(new DishRecord("bread", "Fresh Bread", Material.BREAD,
                DishCategory.BAKED, 5, 6.0f, 1.6f));
        add(new DishRecord("baked-potato", "Baked Potato", Material.BAKED_POTATO,
                DishCategory.PRODUCE, 5, 6.0f, 1.6f));
        add(new DishRecord("pumpkin-pie", "Pumpkin Pie", Material.PUMPKIN_PIE,
                DishCategory.SWEET, 8, 4.8f, 1.6f));
        add(new DishRecord("cookie", "Cookie", Material.COOKIE,
                DishCategory.SWEET, 2, 0.4f, 1.6f));
        add(new DishRecord("cake", "Cake", Material.CAKE,
                DishCategory.SWEET, 2, 0.4f, 1.6f));

        // --- SOUP (village stewpot -- shepherd/farmer overlap) ---
        add(new DishRecord("mushroom-stew", "Mushroom Stew", Material.MUSHROOM_STEW,
                DishCategory.SOUP, 6, 7.2f, 1.6f));
        add(new DishRecord("rabbit-stew", "Rabbit Stew", Material.RABBIT_STEW,
                DishCategory.SOUP, 10, 12.0f, 1.6f));
        add(new DishRecord("suspicious-stew", "Suspicious Stew", Material.SUSPICIOUS_STEW,
                DishCategory.SOUP, 6, 7.2f, 1.6f));
        add(new DishRecord("beetroot-soup", "Beetroot Soup", Material.BEETROOT_SOUP,
                DishCategory.SOUP, 6, 7.2f, 1.6f));
    }

    private DishCatalog() {
    }

    private static void add(DishRecord record) {
        ALL.add(record);
        BY_MATERIAL.put(record.resultMaterial(), record);
    }

    static List<DishRecord> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Null when the given material isn't a dish this plugin's own quality/spoilage/economy
     *  system tracks -- a plain vanilla apple or a raw ingredient someone eats straight, for
     *  instance, is real food but not a "dish" this catalog models. */
    static DishRecord byResultMaterial(Material material) {
        return BY_MATERIAL.get(material);
    }

    static DishRecord byId(String id) {
        for (DishRecord record : ALL) {
            if (record.id().equals(id)) {
                return record;
            }
        }
        return null;
    }

    static boolean isRawIngredient(Material material) {
        return RAW_INGREDIENTS.contains(material);
    }
}
