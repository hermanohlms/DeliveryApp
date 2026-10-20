package com.example.deliveryapp.data;

public enum Branch {
    KENRIDGE(1, "Kenridge"),
    UITZICHT(2, "Uitzicht");

    public final int id;
    public final String displayName;

    Branch(int id, String displayName) { this.id = id; this.displayName = displayName; }

    public static Branch fromId(int id) {
        for (Branch b : values()) if (b.id == id) return b;
        return KENRIDGE;
    }

    public static Branch fromName(String name) {
        for (Branch b : values()) if (b.displayName.equalsIgnoreCase(name)) return b;
        return KENRIDGE;
    }
}
