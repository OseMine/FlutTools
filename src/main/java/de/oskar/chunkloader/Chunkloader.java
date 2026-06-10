package de.oskar.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChunkLoader extends JavaPlugin implements Listener {

    private File dataFile;
    private FileConfiguration dataConfig;

    // Map von Player-UUID zu einer Liste ihrer aktiven Loader-Locations
    private final Map<UUID, List<LoaderInfo>> activeLoaders = new HashMap<>();
    private final Map<UUID, String> discordNames = new HashMap<>(); // NEU: UUID -> Discord-Name
    private final Map<UUID, TimeoutInfo> activeTimeouts = new HashMap<>(); // NEU

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDataFile();
        loadLoadersFromData();
        loadDiscordNamesFromData();
        loadTimeoutsFromData();

        // Befehle registrieren (Brigadier)
        new ChunkLoaderCommand(this).register();
        new AdminCommand(this).register();
        new WhoCommand(this).register();
        new TimeoutCommand(this).register();

        // Listener registrieren
        getServer().getPluginManager().registerEvents(this, this);

        // Task für Timer-Ablauf
        Bukkit.getScheduler().runTaskTimer(this, this::tickLoaders, 20L, 20L);
    }

    @Override
    public void onDisable() {
        saveLoadersToData();
        saveDiscordNamesToData();
        saveTimeoutsToData();
    }
    public Map<UUID, TimeoutInfo> getActiveTimeouts() { return activeTimeouts; }

    // Hilfsmethode: Prüft, ob ein Spieler ein aktives Timeout einer bestimmten Mindeststufe hat
    public boolean hasActiveTimeout(UUID uuid, TimeoutStage requiredStage) {
        TimeoutInfo info = activeTimeouts.get(uuid);
        if (info == null) return false;

        if (info.isExpired()) {
            activeTimeouts.remove(uuid);
            saveTimeoutsToData();
            return false;
        }

        // Wenn die aktuelle Stufe des Spielers gleich oder höher der abgefragten Stufe ist
        return info.getStage().getId() >= requiredStage.getId();
    }
    public Map<UUID, String> getDiscordNames() {
        return discordNames;
    }
    public void saveDiscordNamesToData() {
        dataConfig.set("discord-names", null); // Reset
        for (Map.Entry<UUID, String> entry : discordNames.entrySet()) {
            dataConfig.set("discord-names." + entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDiscordNamesFromData() {
        if (!dataConfig.contains("discord-names")) return;
        for (String uuidStr : dataConfig.getConfigurationSection("discord-names").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            String dcName = dataConfig.getString("discord-names." + uuidStr);
            discordNames.put(uuid, dcName);
        }
    }

    public int getMaxLoaders() {
        return getConfig().getInt("max-loaders", 3);
    }

    public void setMaxLoaders(int max) {
        getConfig().set("max-loaders", max);
        saveConfig();
    }

    public Map<UUID, List<LoaderInfo>> getActiveLoaders() {
        return activeLoaders;
    }

    // Erstellt einen echten Chunk Ticket, damit der Chunk geladen bleibt
    public void addLoader(UUID owner, Location loc, long durationSeconds) {
        Chunk chunk = loc.getChunk();
        // Paper/Spigot Ticket hinzufügen (hält den Chunk im Speicher)
        chunk.addPluginChunkTicket(this);

        long expiry = (durationSeconds == -1) ? -1 : System.currentTimeMillis() + (durationSeconds * 1000);
        LoaderInfo info = new LoaderInfo(loc, expiry);

        activeLoaders.computeIfAbsent(owner, k -> new ArrayList<>()).add(info);
        saveLoadersToData();
    }

    // Entfernt alle Loader eines Spielers
    public void clearAllLoaders() {
        for (List<LoaderInfo> loaders : activeLoaders.values()) {
            for (LoaderInfo info : loaders) {
                info.getLocation().getChunk().removePluginChunkTicket(this);
            }
        }
        activeLoaders.clear();
        saveLoadersToData();
    }

    // Jede Sekunde prüfen, ob temporäre Loader abgelaufen sind
    private void tickLoaders() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, List<LoaderInfo>> entry : activeLoaders.entrySet()) {
            Iterator<LoaderInfo> it = entry.getValue().iterator();
            while (it.hasNext()) {
                LoaderInfo info = it.next();
                if (info.getExpiryTime() != -1 && now > info.getExpiryTime()) {
                    info.getLocation().getChunk().removePluginChunkTicket(this);
                    it.remove();

                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§cEiner deiner Chunk-Loader ist abgelaufen!");
                    }
                }
            }
        }
    }

    // Anforderung: Infinite Loader werden beim Re-Login deaktiviert
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeLoaders.containsKey(uuid)) {
            List<LoaderInfo> loaders = activeLoaders.get(uuid);
            Iterator<LoaderInfo> it = loaders.iterator();
            int removedCount = 0;

            while (it.hasNext()) {
                LoaderInfo info = it.next();
                if (info.getExpiryTime() == -1) { // -1 bedeutet unendlich
                    info.getLocation().getChunk().removePluginChunkTicket(this);
                    it.remove();
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                event.getPlayer().sendMessage("§e[ChunkLoader] " + removedCount + " deiner unendlichen Chunk-Loader wurden deaktiviert, da du dich neu eingeloggt hast.");
                saveLoadersToData();
            }
        }
    }
    // --- TIMEOUT EVENT LISTENER ---

    // Stufe 4: Kann nicht mehr joinen
    @EventHandler
    public void onPlayerPreJoin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        TimeoutInfo info = activeTimeouts.get(uuid);

        if (info != null && info.getStage() == TimeoutStage.BAN) {
            if (info.isExpired()) {
                activeTimeouts.remove(uuid);
            } else {
                long secondsLeft = (info.getExpiryTime() - System.currentTimeMillis()) / 1000;
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        "§cDu hast ein Timeout (Verbleibend: " + secondsLeft + " Sekunden).");
            }
        }
    }

    // Stufe 1: Kann keine Chat-Nachrichten schreiben
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (hasActiveTimeout(event.getPlayer().getUniqueId(), TimeoutStage.CHATHAMMING)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDu kannst nicht chatten. Du hast ein aktives Timeout!");
        }
    }

    // Stufe 3: Kann sich nicht bewegen (Full Lock)
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Ignoriere reine Blickrichtungsänderungen, um Ruckeln zu vermeiden
        if (event.getFrom().getX() == event.getTo().getX() &&
                event.getFrom().getZ() == event.getTo().getZ() &&
                event.getFrom().getY() == event.getTo().getY()) return;

        if (hasActiveTimeout(event.getPlayer().getUniqueId(), TimeoutStage.FULL_LOCK)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDu bist vollständig eingefroren! (Timeout)");
        }
    }

    // Stufe 2: Blöcke abbauen
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (hasActiveTimeout(event.getPlayer().getUniqueId(), TimeoutStage.INTERACTION)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cInteraktion verweigert. Du hast ein aktives Timeout!");
        }
    }

    // Stufe 2: Blöcke platzieren
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (hasActiveTimeout(event.getPlayer().getUniqueId(), TimeoutStage.INTERACTION)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cInteraktion verweigert. Du hast ein aktives Timeout!");
        }
    }

    // Stufe 2: Items nutzen (Essen, Klicken, Interagieren)
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (hasActiveTimeout(event.getPlayer().getUniqueId(), TimeoutStage.INTERACTION)) {
            event.setCancelled(true);
        }
    }

    // Stufe 2: Angreifen (Kampf)
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (hasActiveTimeout(player.getUniqueId(), TimeoutStage.INTERACTION)) {
                event.setCancelled(true);
                player.sendMessage("§cDu kannst im Timeout niemanden angreifen!");
            }
        }
    }

    // --- SPEICHER-LOGIK ---
    public void saveTimeoutsToData() {
        dataConfig.set("timeouts", null);
        for (Map.Entry<UUID, TimeoutInfo> entry : activeTimeouts.entrySet()) {
            String path = "timeouts." + entry.getKey().toString();
            dataConfig.set(path + ".stage", entry.getValue().getStage().getId());
            dataConfig.set(path + ".expiry", entry.getValue().getExpiryTime());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadTimeoutsFromData() {
        if (!dataConfig.contains("timeouts")) return;
        for (String uuidStr : dataConfig.getConfigurationSection("timeouts").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            int stageId = dataConfig.getInt("timeouts." + uuidStr + ".stage");
            long expiry = dataConfig.getLong("timeouts." + uuidStr + ".expiry");

            TimeoutStage stage = TimeoutStage.fromId(stageId);
            if (stage != null && expiry > System.currentTimeMillis()) {
                activeTimeouts.put(uuid, new TimeoutInfo(stage, expiry));
            }
        }
    }
    // --- DATA SAVING & LOADING ---

    private void loadDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveLoadersToData() {
        dataConfig.set("loaders", null); // Reset
        for (Map.Entry<UUID, List<LoaderInfo>> entry : activeLoaders.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<LoaderInfo> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                LoaderInfo info = list.get(i);
                String path = "loaders." + uuidStr + "." + i;
                dataConfig.set(path + ".location", info.getLocation());
                dataConfig.set(path + ".expiry", info.getExpiryTime());
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLoadersFromData() {
        if (!dataConfig.contains("loaders")) return;
        for (String uuidStr : dataConfig.getConfigurationSection("loaders").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            List<LoaderInfo> list = new ArrayList<>();
            for (String indexStr : dataConfig.getConfigurationSection("loaders." + uuidStr).getKeys(false)) {
                Location loc = dataConfig.getLocation("loaders." + uuidStr + "." + indexStr + ".location");
                long expiry = dataConfig.getLong("loaders." + uuidStr + "." + indexStr + ".expiry");

                if (loc != null) {
                    // Erneuere das Ticket beim Serverstart
                    loc.getChunk().addPluginChunkTicket(this);
                    list.add(new LoaderInfo(loc, expiry));
                }
            }
            activeLoaders.put(uuid, list);
        }
    }
}