package me.sirius.ffa.arena;

import java.util.Locale;

public enum ArenaAccess {

    OPEN,
    CLOSED;

    public boolean open() {
        return this == OPEN;
    }

    public boolean closed() {
        return this == CLOSED;
    }

    public static ArenaAccess parse(String raw, ArenaAccess fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "open", "abierta", "abierto", "publica", "public" -> OPEN;
            case "closed", "close", "cerrada", "cerrado", "event", "evento",
                 "events", "eventos", "privada", "private" -> CLOSED;
            default -> fallback;
        };
    }
}
