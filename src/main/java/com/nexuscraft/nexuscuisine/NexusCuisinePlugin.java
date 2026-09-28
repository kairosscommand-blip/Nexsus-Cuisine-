package com.nexuscraft.nexuscuisine;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * NexusCuisine -- a massive overhaul of food, hunger, and cooking, the twelfth entry in the
 * ongoing engine-overhaul series, going after a mechanic genuinely untouched since Beta 1.8: eat
 * an item, hunger bar goes up by a fixed number, nothing else about it has ever mattered. This
 * plugin keeps the real vanilla ritual recognizable -- a real furnace/campfire, a real {@code
 * FurnaceSmeltEvent}, a real eaten {@code ItemStack} -- and overhauls everything underneath it.
 *
 * <p>Four systems layer on top of that real cooking/eating loop: a per-player cooking skill
 * ({@link CookingSkill}/{@link CookingSkillStore}) that rolls a real {@link DishQuality} tier onto
 * every dish a player actually cooks, baked directly into that dish's own real {@code
 * FoodComponent} ({@link DishComponentFactory}) rather than simulated separately; real,
 * check-on-read spoilage ({@link FreshnessCalculator}/{@link CuisineSpoilageService}) that degrades
 * a dish's nutrition the longer it sits uneaten, with a real, craftable paper-wrap preservation
 * method ({@link CuisinePreservationListener}); a real villager food economy ({@link
 * FoodScarcityIndex}/{@link VillagerFoodEconomyService}) that reuses vanilla's own real {@code
 * MerchantRecipe} demand/price fields to make a Farmer/Butcher/Fisherman's trades actually respond
 * to how much a category is being cooked versus eaten server-wide; and an optional, read-only
 * bridge into a real, separately-installed NexusEconomy's own shop pricing ({@link
 * NexusEconomyBridge}), present-if-available and otherwise silently absent.
 *
 * <p>A standalone plugin, no shared compiled dependency with any other sibling -- see README.md's
 * own section for the full reasoning, same convention as every other plugin in this family.
 */
public final class NexusCuisinePlugin extends JavaPlugin {

    private CuisineConfig config;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.config = new CuisineConfig(this);
        config.load(getConfig());

        CookingSkillService skillService = new CookingSkillService(this, config);
        CuisineSpoilageService spoilageService = new CuisineSpoilageService(config, skillService.components());
        VillagerFoodEconomyService economyService = new VillagerFoodEconomyService(config);
        NexusEconomyBridge economyBridge = new NexusEconomyBridge();
        long ownershipWindowMillis = config.furnaceOwnershipWindowMinutes * 60_000L;
        FurnaceOwnershipTracker ownershipTracker = new FurnaceOwnershipTracker(ownershipWindowMillis);

        getServer().getPluginManager().registerEvents(
                new CuisineCookListener(skillService, economyService, ownershipTracker), this);
        getServer().getPluginManager().registerEvents(
                new CuisineVillagerListener(economyService), this);
        getServer().getPluginManager().registerEvents(
                new CuisineConsumptionListener(skillService.components(), spoilageService,
                        economyService, skillService.freshnessTags()),
                this);
        getServer().getPluginManager().registerEvents(
                new CuisinePreservationListener(spoilageService), this);

        long decayPeriodTicks = 20L * 60L * config.economyDecayIntervalMinutes;
        getServer().getScheduler().runTaskTimer(this, () -> economyService.decay(),
                decayPeriodTicks, decayPeriodTicks);

        var command = getCommand("nexuscuisine");
        if (command != null) {
            command.setExecutor(new CuisineCommand(skillService, spoilageService, economyBridge, this::reload));
        }

        getLogger().info("NexusCuisine enabled -- cooking, hunger, and the village food economy have been overhauled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("NexusCuisine disabled.");
    }

    private void reload() {
        reloadConfig();
        config.load(getConfig());
    }
}
