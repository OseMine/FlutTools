package de.of.flutstuff;

import org.bukkit.Location;

public class LoaderInfo {
    private final Location location;
    private final long expiryTime; // Zeitstempel in ms, -1 für unendlich

    public LoaderInfo(Location location, long expiryTime) {
        this.location = location;
        this.expiryTime = expiryTime;
    }

    public Location getLocation() {
        return location;
    }

    public long getExpiryTime() {
        return expiryTime;
    }
}