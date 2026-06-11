package de.of.flutstuff;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class LockCommand {

    private final FlutStuff plugin;
    private final ChestLockListener listener;

    public LockCommand(FlutStuff plugin, ChestLockListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("lock")
                            .requires(src -> src.getSender().hasPermission("flutstuff.lock"))

                            // /lock (Chest anklicken zum Sperren)
                            .executes(ctx -> {
                                if (!(ctx.getSource().getSender() instanceof Player player)) {
                                    ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                    return 0;
                                }
                                listener.getPendingLock().add(player.getUniqueId());
                                player.sendMessage("§eKlicke jetzt eine Chest an, um sie zu sperren.");
                                return 1;
                            })

                            // /lock unlock
                            .then(Commands.literal("unlock")
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }
                                        listener.getPendingUnlock().add(player.getUniqueId());
                                        player.sendMessage("§eKlicke jetzt die Chest an, die du entsperren möchtest.");
                                        return 1;
                                    })
                            )

                            // /lock trust add <spieler>
                            .then(Commands.literal("trust")
                                    .then(Commands.literal("add")
                                            .then(Commands.argument("spieler", ArgumentTypes.player())
                                                    .executes(ctx -> {
                                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                                            return 0;
                                                        }
                                                        PlayerSelectorArgumentResolver selector =
                                                                ctx.getArgument("spieler", PlayerSelectorArgumentResolver.class);
                                                        List<Player> targets = selector.resolve(ctx.getSource());
                                                        if (targets.isEmpty()) {
                                                            player.sendMessage("§cSpieler nicht gefunden.");
                                                            return 0;
                                                        }
                                                        listener.getPendingTrustAdd().add(player.getUniqueId());
                                                        listener.getPendingTrustTarget().put(player.getUniqueId(), targets.get(0).getName());
                                                        player.sendMessage("§eKlicke jetzt die Chest an, zu der §6" + targets.get(0).getName() + " §eZugriff erhalten soll.");
                                                        return 1;
                                                    })
                                            )
                                    )

                                    // /lock trust remove <spieler>
                                    .then(Commands.literal("remove")
                                            .then(Commands.argument("spieler", ArgumentTypes.player())
                                                    .executes(ctx -> {
                                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                                            return 0;
                                                        }
                                                        PlayerSelectorArgumentResolver selector =
                                                                ctx.getArgument("spieler", PlayerSelectorArgumentResolver.class);
                                                        List<Player> targets = selector.resolve(ctx.getSource());
                                                        if (targets.isEmpty()) {
                                                            player.sendMessage("§cSpieler nicht gefunden.");
                                                            return 0;
                                                        }
                                                        listener.getPendingTrustRemove().add(player.getUniqueId());
                                                        listener.getPendingTrustTarget().put(player.getUniqueId(), targets.get(0).getName());
                                                        player.sendMessage("§eKlicke jetzt die Chest an, von der §6" + targets.get(0).getName() + " §eentfernt werden soll.");
                                                        return 1;
                                                    })
                                            )
                                    )
                            )

                            // /lock help
                            .then(Commands.literal("help")
                                    .executes(ctx -> {
                                        ctx.getSource().getSender().sendMessage("§7§m-----------------------------");
                                        ctx.getSource().getSender().sendMessage("§6§lChest-Lock Hilfe");
                                        ctx.getSource().getSender().sendMessage("§7§m-----------------------------");
                                        ctx.getSource().getSender().sendMessage("§e/lock §7- Chest anklicken zum Sperren");
                                        ctx.getSource().getSender().sendMessage("§e/lock unlock §7- Chest anklicken zum Entsperren");
                                        ctx.getSource().getSender().sendMessage("§e/lock trust add <Spieler> §7- Spieler Zugriff geben");
                                        ctx.getSource().getSender().sendMessage("§e/lock trust remove <Spieler> §7- Spieler Zugriff entziehen");
                                        ctx.getSource().getSender().sendMessage("§e/pchest open §7- Öffnet deine private Chest");
                                        ctx.getSource().getSender().sendMessage("§7§m-----------------------------");
                                        return 1;
                                    })
                            )
                            .build(),

                    "Sperrt eine Chest",
                    List.of()
            );
        });
    }
}