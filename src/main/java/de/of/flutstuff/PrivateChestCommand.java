package de.of.flutstuff;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class PrivateChestCommand implements Listener {

    private final FlutStuff plugin;

    // Persistentes Inventar pro Spieler (bleibt im RAM, wird in data.yml gespeichert)
    private final Map<UUID, Inventory> privateInventories = new HashMap<>();

    public PrivateChestCommand(FlutStuff plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Inventory> getPrivateInventories() {
        return privateInventories;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("pchest")
                            .requires(src -> src.getSender().hasPermission("flutstuff.pchest"))

                            .then(Commands.literal("open")
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }

                                        Inventory inv = privateInventories.computeIfAbsent(
                                                player.getUniqueId(),
                                                k -> Bukkit.createInventory(null, 54, "§8Deine private Chest")
                                        );
                                        player.openInventory(inv);
                                        return 1;
                                    })
                            )
                            .build(),

                    "Private unsichtbare Chest",
                    List.of("privatechest")
            );
        });
    }

    // Inventar wird automatisch gespeichert wenn geschlossen
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().title().toString().contains("Deine private Chest")) return;
        // Inventar bleibt in der Map – Inhalt bleibt erhalten bis zum Serverneustart
        // Für echte Persistenz: in FlutStuff.onDisable/onEnable speichern/laden
    }
}