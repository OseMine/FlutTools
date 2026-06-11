package de.of.flutstuff;

import java.util.List;
import java.util.UUID;

public class LockInfo {

    private final UUID owner;
    private final boolean isPrivate;
    private final List<UUID> trusted;

    public LockInfo(UUID owner, boolean isPrivate, List<UUID> trusted) {
        this.owner = owner;
        this.isPrivate = isPrivate;
        this.trusted = trusted;
    }

    public UUID getOwner() { return owner; }
    public boolean isPrivate() { return isPrivate; }
    public List<UUID> getTrusted() { return trusted; }
}