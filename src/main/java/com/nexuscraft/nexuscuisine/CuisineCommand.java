package com.nexuscraft.nexuscuisine;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /nexuscuisine <skill|inspect|price|reload>} -- kept intentionally small: this plugin's
 *  real gameplay loop is cooking, eating, trading, and preserving, all handled through real
 *  vanilla interactions elsewhere; this command is just the read-only status/admin surface. */
final class CuisineCommand implements CommandExecutor {

    private final CookingSkillService skillService;
    private final CuisineSpoilageService spoilageService;
    private final NexusEconomyBridge economyBridge;
    private final Runnable reloadAction;

    CuisineCommand(CookingSkillService skillService, CuisineSpoilageService spoilageService,
                   NexusEconomyBridge economyBridge, Runnable reloadAction) {
        this.skillService = skillService;
        this.spoilageService = spoilageService;
        this.economyBridge = economyBridge;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7/nexuscuisine <skill|inspect|price|reload>");
            return true;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "skill" -> handleSkill(sender);
            case "inspect" -> handleInspect(sender);
            case "price" -> handlePrice(sender, args);
            case "reload" -> handleReload(sender);
            default -> sender.sendMessage("§7/nexuscuisine <skill|inspect|price|reload>");
        }
        return true;
    }

    private void handleSkill(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player has a cooking skill to check.");
            return;
        }
        int level = skillService.levelOf(player);
        long xp = skillService.xpOf(player);
        long nextLevelXp = skillService.xpForNextLevel(player);
        String nextLine = nextLevelXp < 0
                ? "§7(max level)"
                : "§7" + xp + " / " + nextLevelXp + " XP to next level";
        player.sendMessage("§6Cooking level " + level + " §7-- " + xp + " total XP");
        player.sendMessage(nextLine);
    }

    private void handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can inspect a held item.");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String dishId = skillService.components().dishIdOf(held);
        if (dishId == null) {
            player.sendMessage("§7That isn't a dish this plugin has any record of.");
            return;
        }
        DishRecord dish = DishCatalog.byId(dishId);
        DishQuality quality = skillService.components().qualityOf(held);
        SpoilageStage stage = spoilageService.stageOf(held, System.currentTimeMillis());
        boolean preserved = spoilageService.preservedAlready(held);

        player.sendMessage("§6" + (dish != null ? dish.displayName() : dishId));
        if (quality != null) {
            player.sendMessage(quality.display() + " §7-- " + quality.description());
        }
        player.sendMessage(stage.display() + " §7-- " + stage.description());
        if (preserved) {
            player.sendMessage("§bWrapped for preservation.");
        }
    }

    private void handlePrice(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§7/nexuscuisine price <dish-id>");
            return;
        }
        DishRecord dish = DishCatalog.byId(args[1]);
        if (dish == null) {
            sender.sendMessage("§cUnknown dish id.");
            return;
        }
        Double livePrice = economyBridge.liveSellPriceFor(dish.resultMaterial());
        if (livePrice != null) {
            sender.sendMessage("§6" + dish.displayName() + " §7currently sells for §a"
                    + String.format(java.util.Locale.ROOT, "%.2f", livePrice) + " §7in NexusEconomy's shop.");
        } else {
            sender.sendMessage("§6" + dish.displayName() + " §7-- no live NexusEconomy price available right now.");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexuscuisine.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return;
        }
        reloadAction.run();
        sender.sendMessage("§aNexusCuisine config reloaded.");
    }
}
