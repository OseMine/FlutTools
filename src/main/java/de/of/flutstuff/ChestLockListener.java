package de.of.flutstuff;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import java.util.HashMap;
import java.util.Map;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChestLockListener implements Listener {

    private final FlutStuff plugin;
    private final ChestLockManager lockManager;

    // Spieler die gerade auf /lock warten und eine Chest anklicken sollen
    private final Set<UUID> pendingLock = new HashSet<>();
    private final Set<UUID> pendingUnlock = new HashSet<>();
    private final Set<UUID> pendingTrustAdd = new HashSet<>();
    private final Set<UUID> pendingTrustRemove = new HashSet<>();
    private final Map<UUID, String> pendingTrustTarget = new HashMap<>();

    public ChestLockListener(FlutStuff plugin, ChestLockManager lockManager) {
        this.plugin = plugin;
        this.lockManager = lockManager;
    }

    public Set<UUID> getPendingLock() { return pendingLock; }
    public Set<UUID> getPendingUnlock() { return pendingUnlock; }
    public Set<UUID> getPendingTrustAdd() { return pendingTrustAdd; }
    public Set<UUID> getPendingTrustRemove() { return pendingTrustRemove; }
    public Map<UUID, String> getPendingTrustTarget() { return pendingTrustTarget; }

    private boolean isChest(Block block) {
        return block.getType() == Material.CHEST
                || block.getType() == Material.TRAPPED_CHEST
                || block.getType() == Material.BARREL
                || block.getType() == Material.SHULKER_BOX
                || block.getType().name().endsWith("_SHULKER_BOX");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isChest(block)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // --- /lock pending ---
        if (pendingLock.contains(uuid)) {
            event.setCancelled(true);
            pendingLock.remove(uuid);

            if (lockManager.isLocked(block.getLocation())) {
                player.sendMessage("§cDiese Chest ist bereits gesperrt!");
                return;
            }
            lockManager.addLock(block.getLocation(), uuid, false);
            player.sendMessage("§aChest erfolgreich gesperrt!");
            return;
        }

        // --- /unlock pending ---
        if (pendingUnlock.contains(uuid)) {
            event.setCancelled(true);
            pendingUnlock.remove(uuid);

            if (!lockManager.isLocked(block.getLocation())) {
                player.sendMessage("§cDiese Chest ist nicht gesperrt.");
                return;
            }
            if (!lockManager.isOwner(block.getLocation(), uuid)
                    && !player.hasPermission("flutstuff.lock.admin")) {
                player.sendMessage("§cDas ist nicht deine Chest!");
                return;
            }
            lockManager.removeLock(block.getLocation());
            player.sendMessage("§aChest entsperrt!");
            return;
        }

        // --- /trust add pending ---
        if (pendingTrustAdd.contains(uuid)) {
            event.setCancelled(true);
            pendingTrustAdd.remove(uuid);
            String targetName = pendingTrustTarget.remove(uuid);

            if (!lockManager.isLocked(block.getLocation())) {
                player.sendMessage("§cDiese Chest ist nicht gesperrt.");
                return;
            }
            if (!lockManager.isOwner(block.getLocation(), uuid)) {
                player.sendMessage("§cDas ist nicht deine Chest!");
                return;
            }
            Player target = plugin.getServer().getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage("§cSpieler §e" + targetName + " §cist nicht online.");
                return;
            }
            lockManager.addTrusted(block.getLocation(), target.getUniqueId());
            player.sendMessage("§a§e" + target.getName() + " §ahat jetzt Zugriff auf diese Chest.");
            target.sendMessage("§e" + player.getName() + " §ahat dir Zugriff auf eine Chest gegeben.");
            return;
        }

        // --- /trust remove pending ---
        if (pendingTrustRemove.contains(uuid)) {
            event.setCancelled(true);
            pendingTrustRemove.remove(uuid);
            String targetName = pendingTrustTarget.remove(uuid);

            if (!lockManager.isLocked(block.getLocation())) {
                player.sendMessage("§cDiese Chest ist nicht gesperrt.");
                return;
            }
            if (!lockManager.isOwner(block.getLocation(), uuid)) {
                player.sendMessage("§cDas ist nicht deine Chest!");
                return;
            }
            Player target = plugin.getServer().getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage("§cSpieler §e" + targetName + " §cist nicht online.");
                return;
            }
            lockManager.removeTrusted(block.getLocation(), target.getUniqueId());
            player.sendMessage("§e" + target.getName() + " §chat keinen Zugriff mehr auf diese Chest.");
            return;
        }

        // --- Normaler Zugriff: gesperrte Chest ---
        if (lockManager.isLocked(block.getLocation())) {
            if (!lockManager.hasAccess(block.getLocation(), uuid)
                    && !player.hasPermission("flutstuff.lock.admin")) {
                event.setCancelled(true);
                player.sendMessage("§cDiese Chest ist gesperrt!");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isChest(block)) return;
        if (!lockManager.isLocked(block.getLocation())) return;

        Player player = event.getPlayer();

        if (!lockManager.isOwner(block.getLocation(), player.getUniqueId())
                && !player.hasPermission("flutstuff.lock.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cDu kannst diese gesperrte Chest nicht abbauen!");
            return;
        }

        // Besitzer baut ab: Lock automatisch entfernen
        lockManager.removeLock(block.getLocation());
        player.sendMessage("§7Lock wurde automatisch entfernt.");
    }
}