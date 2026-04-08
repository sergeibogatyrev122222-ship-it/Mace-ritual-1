package com.macereveal;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

public class MaceListener implements Listener {

    private final MaceReveal plugin;

    public MaceListener(MaceReveal plugin) {
        this.plugin = plugin;
    }

    /**
     * Fired when a player takes the crafted result from the crafting table.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftMace(CraftItemEvent event) {
        if (event.getRecipe().getResult().getType() != Material.MACE) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        plugin.getRevealManager().startReveal(player);

        player.sendMessage("§6[MaceReveal] §fYour location will be revealed to everyone for §e10 minutes§f!");
        org.bukkit.Bukkit.broadcastMessage(
                "§6[MaceReveal] §e" + player.getName() + " §fhas crafted a mace! Their location is now visible to all!");
    }

    /**
     * Fired when a player clicks inside any inventory.
     * We use this to detect when the mace is taken from the spawned chest.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() != Material.MACE) return;

        // Only care about chest inventories
        if (event.getClickedInventory().getType() != InventoryType.CHEST) return;

        // Check if this chest is one we spawned
        org.bukkit.block.Block chestBlock = null;
        if (event.getView().getTopInventory().getHolder() instanceof org.bukkit.block.Chest chest) {
            chestBlock = chest.getBlock();
        }
        if (chestBlock == null) return;

        RevealManager manager = plugin.getRevealManager();
        if (!manager.isTrackedChest(chestBlock.getLocation())) return;

        // Schedule removal on next tick so the item is actually taken first
        org.bukkit.Location loc = chestBlock.getLocation();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Check chest inventory is now empty of maces
            if (event.getClickedInventory().contains(Material.MACE)) return;
            manager.onMaceTakenFromChest(loc);
        }, 1L);
    }
}
