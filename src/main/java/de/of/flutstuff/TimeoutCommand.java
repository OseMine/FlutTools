package de.of.flutstuff;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class TimeoutCommand {

    private final FlutStuff plugin;

    public TimeoutCommand(FlutStuff plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(                                          // <-- fehlte komplett
                    Commands.literal("timeout")
                            .requires(sender -> sender.getSender().hasPermission("flutstuff.admin")
                                    || sender.getSender().hasPermission("flutstuff.usetimeout"))

                            // /timeout status
                            .then(Commands.literal("status")
                                    .requires(sender -> sender.getSender().hasPermission("flutstuff.usetimeout"))
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }
                                        TimeoutInfo info = plugin.getActiveTimeouts().get(player.getUniqueId());
                                        if (info == null || info.isExpired()) {
                                            player.sendMessage("§aKein aktives Timeout.");
                                            return 1;
                                        }
                                        long secondsLeft = (info.getExpiryTime() - System.currentTimeMillis()) / 1000;
                                        player.sendMessage("§cAktives Timeout: §e" + info.getStage().getDescription());
                                        player.sendMessage("§7Verbleibend: §e" + secondsLeft + " Sekunden");
                                        return 1;
                                    })
                            )

                            // /timeout <spieler> <stufe> <sekunden>
                            .then(Commands.argument("spieler", ArgumentTypes.player())
                                    .requires(sender -> sender.getSender().hasPermission("flutstuff.timeout.give"))
                                    .then(Commands.argument("stufe", IntegerArgumentType.integer(1, 4))
                                            .then(Commands.argument("sekunden", LongArgumentType.longArg(1))
                                                    .executes(ctx -> {
                                                        PlayerSelectorArgumentResolver selector =
                                                                ctx.getArgument("spieler", PlayerSelectorArgumentResolver.class);
                                                        List<Player> targets = selector.resolve(ctx.getSource());

                                                        if (targets.isEmpty()) {
                                                            ctx.getSource().getSender().sendMessage("§cSpieler nicht gefunden.");
                                                            return 0;
                                                        }

                                                        Player target = targets.get(0);
                                                        int stageId = IntegerArgumentType.getInteger(ctx, "stufe");
                                                        long seconds = LongArgumentType.getLong(ctx, "sekunden");

                                                        return applyTimeout(ctx.getSource().getSender(), target.getUniqueId(), target.getName(), stageId, seconds);
                                                    })
                                            )
                                    )
                            )
                            // /timeout remove <spieler>
                            .then(Commands.literal("remove")
                                    .requires(sender -> sender.getSender().hasPermission("flutstuff.timeout.give"))
                                    .then(Commands.argument("spieler", ArgumentTypes.player())
                                            .executes(ctx -> {
                                                PlayerSelectorArgumentResolver selector =
                                                        ctx.getArgument("spieler", PlayerSelectorArgumentResolver.class);
                                                List<Player> targets = selector.resolve(ctx.getSource());

                                                if (targets.isEmpty()) {
                                                    ctx.getSource().getSender().sendMessage("§cSpieler nicht gefunden.");
                                                    return 0;
                                                }

                                                Player target = targets.get(0);
                                                if (plugin.getActiveTimeouts().remove(target.getUniqueId()) != null) {
                                                    plugin.saveTimeoutsToData();
                                                    ctx.getSource().getSender().sendMessage("§aTimeout von §e" + target.getName() + " §aaufgehoben.");
                                                    target.sendMessage("§aDein Timeout wurde aufgehoben!");
                                                } else {
                                                    ctx.getSource().getSender().sendMessage("§e" + target.getName() + " §chat kein aktives Timeout.");
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .build(),                                           // <-- jetzt am richtigen Ort

                    "Verhänge ein Timeout über einen Spieler",
                    List.of("cltimeout")
            );
        });
    }

    private int applyTimeout(CommandSender sender, UUID targetUUID, String targetName, int stageId, long seconds) {
        // Immunitäts-Check
        Player targetOnline = Bukkit.getPlayer(targetUUID);
        if (targetOnline != null && targetOnline.hasPermission("flutstuff.timeout.immune")) {
            sender.sendMessage("§c" + targetName + " §7ist immun gegen Timeouts.");
            return 0;
        }
        TimeoutStage stage = TimeoutStage.fromId(stageId);
        if (stage == null) {
            sender.sendMessage("§cUngültige Stufe! Nutze 1–4.");
            return 0;
        }

        long expiry = System.currentTimeMillis() + (seconds * 1000);
        plugin.getActiveTimeouts().put(targetUUID, new TimeoutInfo(stage, expiry));
        plugin.saveTimeoutsToData();

        sender.sendMessage("§aTimeout für §e" + targetName + " §averhängt.");
        sender.sendMessage("§7Stufe: §b" + stage.getDescription() + " §7| Dauer: §b" + seconds + "s");

        Player online = Bukkit.getPlayer(targetUUID);
        if (online != null) {
            if (stage == TimeoutStage.BAN) {
                online.kickPlayer("§cDu hast ein Timeout erhalten! Verbleibend: " + seconds + " Sekunden.");
            } else {
                online.sendMessage("§c§lDu hast ein Timeout erhalten!");
                online.sendMessage("§7Einschränkung: §e" + stage.getDescription());
                online.sendMessage("§7Dauer: §e" + seconds + " Sekunden.");
            }
        }
        return 1;
    }
}