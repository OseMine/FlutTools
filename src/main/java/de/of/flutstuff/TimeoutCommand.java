package de.of.flutstuff;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class TimeoutCommand {

    private final FlutStuff plugin;

    public TimeoutCommand(FlutStuff plugin) {
        this.plugin = plugin;
    }

    // Autocomplete: nur Spieler die aktuell ein Timeout haben + "all"
    private SuggestionProvider<CommandSourceStack> timeoutPlayerSuggestions() {
        return (ctx, builder) -> {
            builder.suggest("all");
            for (Map.Entry<UUID, TimeoutInfo> entry : plugin.getActiveTimeouts().entrySet()) {
                if (!entry.getValue().isExpired()) {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    if (name != null) builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    Commands.literal("timeout")
                            .requires(sender -> sender.getSender().hasPermission("flutstuff.timeout.give")
                                    || sender.getSender().hasPermission("flutstuff.usetimeout"))

                            // /timeout help
                            .then(Commands.literal("help")
                                    .requires(sender -> sender.getSender().hasPermission("flutstuff.timeout.give")
                                            || sender.getSender().hasPermission("flutstuff.usetimeout"))
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        boolean isAdmin = sender.hasPermission("flutstuff.timeout.give");

                                        sender.sendMessage("§7§m-----------------------------");
                                        sender.sendMessage("§6§lTimeout-Hilfe");
                                        sender.sendMessage("§7§m-----------------------------");

                                        // Jeder mit flutstuff.usetimeout sieht das:
                                        if (sender.hasPermission("flutstuff.usetimeout")) {
                                            sender.sendMessage("§6Deine Befehle:");
                                            sender.sendMessage("§e/timeout help §7- Zeigt diese Hilfe");
                                            sender.sendMessage("§e/timeout status §7- Zeigt dein eigenes aktives Timeout");
                                        }

                                        // Admins sehen zusätzlich:
                                        if (isAdmin) {
                                            sender.sendMessage(" ");
                                            sender.sendMessage("§6Admin-Befehle:");
                                            sender.sendMessage("§e/timeout status <Spieler> §7- Zeigt Timeout eines bestimmten Spielers");
                                            sender.sendMessage("§e/timeout status all §7- Listet alle aktiven Timeouts auf");
                                            sender.sendMessage("§e/timeout <Spieler> <Stufe 1-4> <Sekunden> §7- Verhängt ein Timeout");
                                            sender.sendMessage("§e/timeout remove <Spieler> §7- Hebt ein Timeout auf");
                                        }

                                        // Stufen sehen alle (zur Info was sie erwartet)
                                        sender.sendMessage(" ");
                                        sender.sendMessage("§6§lTimeout-Stufen:");
                                        sender.sendMessage("§e§lStufe 1 §r§7- §fChat-Sperre");
                                        sender.sendMessage("§8  » Kann keine Nachrichten im Chat schreiben.");
                                        sender.sendMessage("§e§lStufe 2 §r§7- §fInteraktionssperre");
                                        sender.sendMessage("§8  » Kann nicht abbauen, bauen, kämpfen oder Items benutzen.");
                                        sender.sendMessage("§e§lStufe 3 §r§7- §fFull Lock");
                                        sender.sendMessage("§8  » Kann sich nicht bewegen und nichts tun. Komplett eingefroren.");
                                        sender.sendMessage("§e§lStufe 4 §r§7- §fBan");
                                        sender.sendMessage("§8  » Kann den Server nicht betreten bis das Timeout abläuft.");

                                        sender.sendMessage("§7§m-----------------------------");
                                        return 1;
                                    })
                            )

                            // /timeout status (eigenes)
                            .then(Commands.literal("status")
                                    .requires(sender -> sender.getSender().hasPermission("flutstuff.usetimeout"))
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }
                                        showStatus(ctx.getSource().getSender(), player.getUniqueId(), player.getName());
                                        return 1;
                                    })

                                    // /timeout status <spieler|all> (nur Admin)
                                    .then(Commands.argument("ziel", com.mojang.brigadier.arguments.StringArgumentType.word())
                                            .requires(sender -> sender.getSender().hasPermission("flutstuff.timeout.give"))
                                            .suggests(timeoutPlayerSuggestions())                                            .executes(ctx -> {
                                                String ziel = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ziel");
                                                CommandSender sender = ctx.getSource().getSender();

                                                // "all" zeigt alle aktiven Timeouts
                                                if (ziel.equalsIgnoreCase("all")) {
                                                    showAll(sender);
                                                    return 1;
                                                }

                                                // Einzelner Spieler
                                                @SuppressWarnings("deprecation")
                                                var target = Bukkit.getOfflinePlayer(ziel);
                                                if (target == null) {
                                                    sender.sendMessage("§cSpieler nicht gefunden.");
                                                    return 0;
                                                }
                                                showStatus(sender, target.getUniqueId(), ziel);
                                                return 1;
                                            })
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
                            .build(),

                    "Timeout-Verwaltung",
                    List.of("cltimeout")
            );
        });
    }

    private void showStatus(CommandSender sender, UUID uuid, String name) {
        TimeoutInfo info = plugin.getActiveTimeouts().get(uuid);
        if (info == null || info.isExpired()) {
            sender.sendMessage("§e" + name + " §ahat kein aktives Timeout.");
            return;
        }
        long secondsLeft = (info.getExpiryTime() - System.currentTimeMillis()) / 1000;
        sender.sendMessage("§7§m-----------------------------");
        sender.sendMessage("§6Timeout: §e" + name);
        sender.sendMessage("§7Stufe: §e" + info.getStage().getId() + " §7- §f" + info.getStage().getDescription());
        sender.sendMessage("§7Verbleibend: §e" + secondsLeft + " Sekunden");
        sender.sendMessage("§7§m-----------------------------");
    }

    private void showAll(CommandSender sender) {
        Map<UUID, TimeoutInfo> timeouts = plugin.getActiveTimeouts();

        // Abgelaufene zuerst rausfiltern
        timeouts.entrySet().removeIf(e -> e.getValue().isExpired());

        if (timeouts.isEmpty()) {
            sender.sendMessage("§aKeine aktiven Timeouts.");
            return;
        }

        sender.sendMessage("§7§m-----------------------------");
        sender.sendMessage("§6§lAktive Timeouts (" + timeouts.size() + ")");
        sender.sendMessage("§7§m-----------------------------");

        for (Map.Entry<UUID, TimeoutInfo> entry : timeouts.entrySet()) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = entry.getKey().toString();
            long secondsLeft = (entry.getValue().getExpiryTime() - System.currentTimeMillis()) / 1000;
            sender.sendMessage("§e" + name
                    + " §7| Stufe: §b" + entry.getValue().getStage().getId()
                    + " §7| §b" + secondsLeft + "s §7verbleibend");
        }

        sender.sendMessage("§7§m-----------------------------");
    }

    private int applyTimeout(CommandSender sender, UUID targetUUID, String targetName, int stageId, long seconds) {
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

        if (targetOnline != null) {
            if (stage == TimeoutStage.BAN) {
                targetOnline.kickPlayer("§cDu hast ein Timeout erhalten! Verbleibend: " + seconds + " Sekunden.");
            } else {
                targetOnline.sendMessage("§c§lDu hast ein Timeout erhalten!");
                targetOnline.sendMessage("§7Einschränkung: §e" + stage.getDescription());
                targetOnline.sendMessage("§7Dauer: §e" + seconds + " Sekunden.");
            }
        }
        return 1;
    }
}