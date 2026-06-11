package de.of.flutstuff;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChestLockManager {

    private final FlutStuff plugin;
    private File lockFile;
    private FileConfiguration lockConfig;

    // Location-String -> LockInfo
    private final Map<String, LockInfo> locks = new HashMap<>();

    public ChestLockManager(FlutStuff plugin) {
        this.plugin = plugin;
        loadFile();
        loadFromFile();
    }

    // --- Hilfsmethoden ---

    public String locKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public boolean isLocked(Location loc) {
        return locks.containsKey(locKey(loc));
    }

    public LockInfo getLock(Location loc) {
        return locks.get(locKey(loc));
    }

    public void addLock(Location loc, UUID owner, boolean isPrivate) {
        locks.put(locKey(loc), new LockInfo(owner, isPrivate, new ArrayList<>()));
        save();
    }

    public void removeLock(Location loc) {
        locks.remove(locKey(loc));
        save();
    }

    public boolean isOwner(Location loc, UUID uuid) {
        LockInfo info = getLock(loc);
        return info != null && info.getOwner().equals(uuid);
    }

    public boolean hasAccess(Location loc, UUID uuid) {
        LockInfo info = getLock(loc);
        if (info == null) return true;
        return info.getOwner().equals(uuid) || info.getTrusted().contains(uuid);
    }

    public void addTrusted(Location loc, UUID uuid) {
        LockInfo info = getLock(loc);
        if (info != null && !info.getTrusted().contains(uuid)) {
            info.getTrusted().add(uuid);
            save();
        }
    }

    public void removeTrusted(Location loc, UUID uuid) {
        LockInfo info = getLock(loc);
        if (info != null) {
            info.getTrusted().remove(uuid);
            save();
        }
    }

    // --- Persistenz ---

    private void loadFile() {
        lockFile = new File(plugin.getDataFolder(), "locks.yml");
        if (!lockFile.exists()) {
            try {
                lockFile.getParentFile().mkdirs();
                lockFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        lockConfig = YamlConfiguration.loadConfiguration(lockFile);
    }

    private void loadFromFile() {
        if (!lockConfig.contains("locks")) return;
        for (String key : lockConfig.getConfigurationSection("locks").getKeys(false)) {
            String path = "locks." + key;
            UUID owner = UUID.fromString(lockConfig.getString(path + ".owner"));
            boolean isPrivate = lockConfig.getBoolean(path + ".private");
            List<UUID> trusted = new ArrayList<>();
            for (String uuidStr : lockConfig.getStringList(path + ".trusted")) {
                trusted.add(UUID.fromString(uuidStr));
            }
            locks.put(key, new LockInfo(owner, isPrivate, trusted));
        }
    }

    public void save() {
        lockConfig.set("locks", null);
        for (Map.Entry<String, LockInfo> entry : locks.entrySet()) {
            String path = "locks." + entry.getKey();
            lockConfig.set(path + ".owner", entry.getValue().getOwner().toString());
            lockConfig.set(path + ".private", entry.getValue().isPrivate());
            List<String> trustedStrings = new ArrayList<>();
            for (UUID uuid : entry.getValue().getTrusted()) {
                trustedStrings.add(uuid.toString());
            }
            lockConfig.set(path + ".trusted", trustedStrings);
        }
        try {
            lockConfig.save(lockFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}