package com.nexuscraft.nexuscuisine;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * The one Bukkit-facing entry point {@code CuisineCookListener} calls whenever a real furnace or
 * campfire actually finishes cooking something this plugin cares about -- ties together a cook's
 * stored skill ({@link CookingSkillStore}), the ingredient's real freshness ({@link
 * IngredientFreshnessTags}), the pure quality roll ({@link CookingSkill}), and the real item this
 * all turns into ({@link DishComponentFactory}) into a single call.
 */
final class CookingSkillService {

    private final CuisineConfig config;
    private final CookingSkillStore skillStore;
    private final DishComponentFactory componentFactory;
    private final IngredientFreshnessTags freshnessTags;
    private final Random random = new Random();

    CookingSkillService(Plugin plugin, CuisineConfig config) {
        this.config = config;
        this.skillStore = new CookingSkillStore(plugin);
        this.componentFactory = new DishComponentFactory(plugin);
        this.freshnessTags = new IngredientFreshnessTags(plugin);
    }

    /**
     * Resolves one completed cook into the real, physical dish item -- rolls this cook's quality
     * tier off their current skill level and the source ingredient's real freshness, awards the
     * XP that roll earned, and hands back a fully built, PDC-tagged {@code ItemStack} ready to
     * replace whatever a real {@code FurnaceSmeltEvent}/{@code CampfireStartEvent} was about to
     * produce.
     */
    ItemStack resolveCook(Player cook, DishRecord dish, ItemStack sourceIngredient, long nowMillis) {
        int level = skillStore.levelOf(cook, config);
        double freshness = freshnessTags.freshnessOf(sourceIngredient, nowMillis, config);
        DishQuality quality = CookingSkill.rollQuality(level, freshness, random, config);
        long xpAwarded = CookingSkill.xpForDish(quality);
        skillStore.addXp(cook, xpAwarded);

        CookRecord record = new CookRecord(cook.getUniqueId(), dish.id(), quality, nowMillis, xpAwarded);
        return componentFactory.buildDish(dish, record, random);
    }

    int levelOf(Player player) {
        return skillStore.levelOf(player, config);
    }

    long xpOf(Player player) {
        return skillStore.xpOf(player);
    }

    long xpForNextLevel(Player player) {
        return CookingSkill.xpForNextLevel(levelOf(player), config);
    }

    /** Exposed so {@code CuisineSpoilageService} and the {@code /nexuscuisine} command can read
     *  back the same PDC tags this service's own {@link DishComponentFactory} writes, without
     *  either one owning a second copy of the tagging keys. */
    DishComponentFactory components() {
        return componentFactory;
    }

    /** Exposed so {@code CuisineIngredientListener} tags the same real PDC key this service
     *  itself reads freshness off of, rather than a second instance risking a different key. */
    IngredientFreshnessTags freshnessTags() {
        return freshnessTags;
    }
}
