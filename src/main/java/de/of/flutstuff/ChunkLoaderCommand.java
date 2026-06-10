package de.of.flutstuff;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class ChunkLoaderCommand {

    private final FlutStuff plugin;

    public ChunkLoaderCommand(FlutStuff plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("cl")
                            .requires(sender -> sender.getSender().hasPermission("flutstuff.use"))

                            // /cl infinite
                            .then(Commands.literal("infinite")
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }
                                        return placeLoader(player, -1);
                                    })
                                    .build()
                            )

                            // /cl <Sekunden>
                            .then(Commands.argument("sekunden", LongArgumentType.longArg(1))
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }
                                        long duration = LongArgumentType.getLong(ctx, "sekunden");
                                        return placeLoader(player, duration);
                                    })
                            )

                            // /cl config <Anzahl>
                            .then(Commands.literal("config")
                                    .requires(sender -> sender.getSender().hasPermission("flutstuff.admin"))
                                    .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                int max = IntegerArgumentType.getInteger(ctx, "limit");
                                                plugin.setMaxLoaders(max);
                                                ctx.getSource().getSender().sendMessage("§aMaximales Limit auf §e" + max + " §agesetzt.");
                                                return 1;
                                            })
                                    )
                            )

                            // /cl (kein Argument)
                            .executes(ctx -> {
                                ctx.getSource().getSender().sendMessage("§6Verwendung: §7/cl <Sekunden | infinite>");
                                if (ctx.getSource().getSender().hasPermission("flutstuff.admin")) {
                                    ctx.getSource().getSender().sendMessage("§7/cl config <Anzahl> §e- Setzt das maximale Limit.");
                                }
                                return 1;
                            })
                            .build(),

                    "Verwalte deine Chunk-Loader",
                    List.of("chunkloader")
            );
        });
    }

    private int placeLoader(Player player, long duration) {
        List<LoaderInfo> playerLoaders = plugin.getActiveLoaders().get(player.getUniqueId());
        int currentCount = (playerLoaders == null) ? 0 : playerLoaders.size();

        if (currentCount >= plugin.getMaxLoaders()) {
            player.sendMessage("§cLimit von §e" + plugin.getMaxLoaders() + " §cChunk-Loadern erreicht!");
            return 0;
        }

        plugin.addLoader(player.getUniqueId(), player.getLocation(), duration);

        if (duration == -1) {
            player.sendMessage("§aUnendlicher Chunk-Loader gesetzt! §7(Wird beim Re-Login deaktiviert)");
        } else {
            player.sendMessage("§aChunk-Loader für §e" + duration + " Sekunden §agesetzt!");
        }
        return 1;
    }
}