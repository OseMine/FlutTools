package de.of.flutstuff;

public class TimeoutInfo {
    private final TimeoutStage stage;
    private final long expiryTime; // Zeitstempel in ms

    public TimeoutInfo(TimeoutStage stage, long expiryTime) {
        this.stage = stage;
        this.expiryTime = expiryTime;
    }

    public TimeoutStage getStage() { return stage; }
    public long getExpiryTime() { return expiryTime; }
    public boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
}