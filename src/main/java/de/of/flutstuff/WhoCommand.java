package de.of.flutstuff;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class WhoCommand {

    private final FlutStuff plugin;

    public WhoCommand(FlutStuff plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            // /who
            commands.register(
                    Commands.literal("who")
                            .requires(sender -> sender.getSender().hasPermission("flutstuff.who"))
                            .executes(ctx -> {
                                showList(ctx.getSource().getSender());
                                return 1;
                            })
                            .build(),

                    "Zeigt alle Spieler und ihre Discord-Namen",
                    List.of()
            );

            // /whoreg <Discord-Name>
            commands.register(
                    Commands.literal("who:reg")
                            .requires(sender -> sender.getSender().hasPermission("flutstuff.who.register"))
                            .then(Commands.argument("discord-name", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                                            ctx.getSource().getSender().sendMessage("§cNur für Spieler!");
                                            return 0;
                                        }
                                        String dcName = StringArgumentType.getString(ctx, "discord-name");
                                        plugin.getDiscordNames().put(player.getUniqueId(), dcName);
                                        plugin.saveDiscordNamesToData();
                                        player.sendMessage("§aVerknüpft mit Discord: §e" + dcName);
                                        return 1;
                                    })
                            )
                            .executes(ctx -> {
                                ctx.getSource().getSender().sendMessage("§cVerwendung: §7/whoreg <Discord-Name>");
                                return 0;
                            })
                            .build(),

                    "Verknüpfe deinen Account mit deinem Discord-Namen",
                    List.of()
            );
        });
    }

    private void showList(CommandSender sender) {
        Map<UUID, String> dcNames = plugin.getDiscordNames();
        if (dcNames.isEmpty()) {
            sender.sendMessage("§cNoch keine Spieler registriert.");
            return;
        }
        sender.sendMessage("§7--- §6Spieler-Discord-Liste §7---");
        for (Map.Entry<UUID, String> entry : dcNames.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String mcName = op.getName() != null ? op.getName() : "Unbekannt";
            String status = op.isOnline() ? "§a●" : "§c●";
            sender.sendMessage(status + " §e" + mcName + " §7➔ §b" + entry.getValue());
        }
        sender.sendMessage("§7-----------------------------");
    }
}