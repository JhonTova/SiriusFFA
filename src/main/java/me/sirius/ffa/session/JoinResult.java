package me.sirius.ffa.session;

public enum JoinResult {

    OK(""),
    ALREADY_IN_ARENA("join.already-in-arena"),
    ALREADY_IN_EVENT("join.already-in-event"),
    JOINING("join.in-progress"),
    ARENA_NOT_FOUND("arena.not-found"),
    ARENA_DISABLED("arena.disabled"),
    ARENA_NOT_READY("arena.not-ready"),
    ARENA_FULL("join.arena-full"),
    ARENA_RESERVED("join.arena-reserved"),
    ARENAS_CLOSED("join.arenas-closed"),
    INVENTORY_NOT_EMPTY("join.inventory-not-empty"),
    KIT_MISSING("join.kit-missing"),
    NO_PERMISSION("general.no-permission"),
    IN_COMBAT("join.in-combat"),
    BACKUP_FAILED("join.backup-failed"),
    EVENT_NOT_RUNNING("event.not-running"),
    EVENT_CLOSED("event.closed"),
    EVENT_FULL("event.full"),
    DEAD("join.dead");

    private final String message;

    JoinResult(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public boolean ok() {
        return this == OK;
    }
}
