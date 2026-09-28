package com.nexuscraft.nexuscuisine;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * The two real, player-facing moments this plugin's freshness/economy loop actually closes on:
 * a tracked dish being eaten (spoilage is refreshed one last time right before it takes effect,
 * and the real consumption becomes a real, observable demand signal for {@link
 * VillagerFoodEconomyService}), and a raw ingredient this plugin cares about being picked up
 * (tagged with a real harvested-at timestamp so a later cook can roll its freshness).
 *
 * <p>REVISED after a real {@code mvn clean package} against real paper-api 1.21.4-R0.1-SNAPSHOT
 * disagreed with this plugin's original {@code FoodComponent} shape -- see that interface's own
 * class comment for the full real-build correction. This listener is now also where the
 * quality-gated bonus effect and the spoiled-dish Hunger penalty actually get applied: both used
 * to be baked onto the dish's own {@code FoodComponent}, a real surface that turned out not to
 * expose either at this project's target version. Rolling the bonus chance here, at the moment of
 * real consumption, is if anything closer to real vanilla's own rotten-flesh/pufferfish behavior
 * anyway -- that's a real per-eat chance the game engine rolls, not a fixed decision baked into a
 * specific item the moment it's created.
 */
final class CuisineConsumptionListener implements Listener {

    private final DishComponentFactory components;
    private final CuisineSpoilageService spoilageService;
    private final VillagerFoodEconomyService economyService;
    private final IngredientFreshnessTags freshnessTags;
    private final Random random = new Random();

    CuisineConsumptionListener(DishComponentFactory components, CuisineSpoilageService spoilageService,
                               VillagerFoodEconomyService economyService, IngredientFreshnessTags freshnessTags) {
        this.components = components;
        this.spoilageService = spoilageService;
        this.economyService = economyService;
        this.freshnessTags = freshnessTags;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        String dishId = components.dishIdOf(item);
        if (dishId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        spoilageService.refresh(item, now);
        // Real Bukkit contract (see this stub's own comment on PlayerItemConsumeEvent): mutating
        // the returned ItemStack in place does nothing on a real server -- it must be handed back
        // through setItem() to actually apply.
        event.setItem(item);

        Player player = event.getPlayer();

        DishRecord dish = DishCatalog.byId(dishId);
        if (dish != null) {
            economyService.recordConsumption(dish.category());

            DishQuality quality = components.qualityOf(item);
            if (quality != null && random.nextDouble() < quality.bonusEffectChance()) {
                player.addPotionEffect(new PotionEffect(dish.category().bonusEffectType(), 200, 0));
            }
        }

        if (spoilageService.stageOf(item, now) == SpoilageStage.SPOILED) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 260, 0));
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!DishCatalog.isRawIngredient(stack.getType())) {
            return;
        }
        freshnessTags.tagIfAbsent(stack, System.currentTimeMillis());
    }
}
