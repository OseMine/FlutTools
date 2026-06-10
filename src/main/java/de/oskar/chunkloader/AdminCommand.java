package de.oskar.chunkloader;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class AdminCommand {

    private final ChunkLoader plugin;

    public AdminCommand(ChunkLoader plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("cladmin")
                            .requires(sender -> sender.getSender().hasPermission("chunkloader.admin"))
                            .executes(ctx -> {
                                plugin.clearAllLoaders();
                                ctx.getSource().getSender().sendMessage("§aAlle Chunk-Loader gelöscht.");
                                return 1;
                            })
                            .build(), // <-- das fehlte
                    "Admin-Befehl zum Verwalten aller Chunk-Loader",
                    List.of()
            );
        });
    }
}