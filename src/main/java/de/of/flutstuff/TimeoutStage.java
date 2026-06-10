package de.of.flutstuff;

public enum TimeoutStage {
    CHATHAMMING(1, "Chat-Sperre"),
    INTERACTION(2, "Interaktions-Sperre (Blöcke/Items/Kampf)"),
    FULL_LOCK(3, "Vollständige Sperre (Kann nichts tun)"),
    BAN(4, "Netzwerk-Ausschluss (Kann nicht joinen)");

    private final int id;
    private final String description;

    TimeoutStage(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() { return id; }
    public String getDescription() { return description; }

    public static TimeoutStage fromId(int id) {
        for (TimeoutStage stage : values()) {
            if (stage.id == id) return stage;
        }
        return null;
    }
}