package com.macereveal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RevealManager {

    private final MaceReveal plugin;

    // Tracks active reveal sessions: crafter UUID -> their tasks
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> chestSpawnTasks = new HashMap<>();

    // Tracks spawned chest locations so we can remove them
    private final Map<UUID, Location> spawnedChests = new HashMap<>();

    // Rainbow colours cycling through the spectrum
    private static final int[] RAINBOW_COLORS = {
        0xFF0000, 0xFF4400, 0xFF8800, 0xFFCC00, 0xFFFF00,
        0x88FF00, 0x00FF00, 0x00FF88, 0x00FFFF, 0x0088FF,
        0x0000FF, 0x8800FF, 0xFF00FF, 0xFF0088
    };

    private final Random random = new Random();

    public RevealManager(MaceReveal plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a reveal session for a player who just crafted a mace.
     */
    public void startReveal(Player crafter) {
        UUID id = crafter.getUniqueId();

        // Cancel any existing session for this player
        cancelSession(id);

        // --- Action bar task: updates every 15 seconds (300 ticks) ---
        BukkitTask actionBarTask = new BukkitRunnable() {
            int tick = 0;
            int colorOffset = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                String name = p != null ? p.getName() : crafter.getName();
                Location loc = p != null ? p.getLocation() : null;

                String coordText;
                if (loc != null) {
                    coordText = name + " | X: " + loc.getBlockX()
                            + "  Y: " + loc.getBlockY()
                            + "  Z: " + loc.getBlockZ();
                } else {
                    coordText = name + " | (offline)";
                }

                Component rainbow = buildRainbow(coordText, colorOffset);
                colorOffset = (colorOffset + 1) % RAINBOW_COLORS.length;

                // Send to every online player
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendActionBar(rainbow);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 15 * 20L); // every 15 seconds

        // --- Chest spawn task: fires once after 10 minutes (12000 ticks) ---
        BukkitTask chestTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Action bar keeps running — it will stop when mace is taken from chest

                // Find crafter's current location
                Player p = Bukkit.getPlayer(id);
                if (p == null) {
                    // Player offline — stop everything
                    cancelSession(id);
                    return;
                }

                Location chest = findChestLocation(p.getLocation());
                if (chest == null) {
                    plugin.getLogger().warning("Could not find a valid chest location near " + p.getName());
                    return;
                }

                spawnChest(id, chest);

                String msg = "§6[MaceReveal] §fA chest containing a mace has spawned near §e"
                        + p.getName() + "§f! Take the mace to stop the reveal!";
                Bukkit.broadcastMessage(msg);
            }
        }.runTaskLater(plugin, 10 * 60 * 20L); // 10 minutes

        actionBarTasks.put(id, actionBarTask);
        chestSpawnTasks.put(id, chestTask);
    }

    /**
     * Spawn a chest at the given location with a mace inside.
     */
    private void spawnChest(UUID crafterId, Location loc) {
        Block block = loc.getBlock();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest chest) {
            chest.getInventory().setItem(13, new ItemStack(Material.MACE)); // centre slot
            chest.update();
        }

        spawnedChests.put(crafterId, loc.clone());
    }

    /**
     * Called when a mace is taken from a tracked chest.
     */
    public void onMaceTakenFromChest(Location chestLoc) {
        UUID owner = null;
        for (Map.Entry<UUID, Location> entry : spawnedChests.entrySet()) {
            if (isSameBlock(entry.getValue(), chestLoc)) {
                owner = entry.getKey();
                break;
            }
        }
        if (owner == null) return;

        spawnedChests.remove(owner);
        chestLoc.getBlock().setType(Material.AIR);

        // Stop the action bar now that mace is taken
        BukkitTask ab = actionBarTasks.remove(owner);
        if (ab != null) ab.cancel();

        // Clear action bar for everyone
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendActionBar(Component.empty());
        }

        Bukkit.broadcastMessage("§6[MaceReveal] §fThe mace has been claimed! Location reveal ended.");
    }

    /**
     * Check if a location is a tracked chest.
     */
    public boolean isTrackedChest(Location loc) {
        for (Location cl : spawnedChests.values()) {
            if (isSameBlock(cl, loc)) return true;
        }
        return false;
    }

    /**
     * Find a random air block within 15 blocks of the given location to place the chest.
     */
    private Location findChestLocation(Location origin) {
        List<Location> candidates = new ArrayList<>();

        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    Location candidate = origin.clone().add(dx, dy, dz);
                    Block b = candidate.getBlock();
                    Block below = candidate.clone().add(0, -1, 0).getBlock();

                    if (b.getType() == Material.AIR
                            && below.getType().isSolid()
                            && candidate.distance(origin) <= 15) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Build a rainbow-coloured Component from a string.
     */
    private Component buildRainbow(String text, int offset) {
        Component result = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            int colorHex = RAINBOW_COLORS[(i + offset) % RAINBOW_COLORS.length];
            result = result.append(
                Component.text(String.valueOf(text.charAt(i)))
                    .color(TextColor.color(colorHex))
            );
        }
        return result;
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public void cancelSession(UUID id) {
        BukkitTask ab = actionBarTasks.remove(id);
        if (ab != null) ab.cancel();
        BukkitTask ct = chestSpawnTasks.remove(id);
        if (ct != null) ct.cancel();
    }

    public void cancelAll() {
        actionBarTasks.values().forEach(BukkitTask::cancel);
        chestSpawnTasks.values().forEach(BukkitTask::cancel);
        actionBarTasks.clear();
        chestSpawnTasks.clear();
    }
}
