package me.sirius.ffa.session;

public enum LeaveReason {
    COMMAND(true, true),
    EXIT_COMMAND(false, true),
    QUIT(true, false),
    EVENT_END(true, true),
    ELIMINATED(true, true),
    ADMIN(true, true),
    SHUTDOWN(true, false);

    private final boolean teleportBack;
    private final boolean announce;

    LeaveReason(boolean teleportBack, boolean announce) {
        this.teleportBack = teleportBack;
        this.announce = announce;
    }

    public boolean teleportBack() {
        return teleportBack;
    }

    public boolean announces() {
        return announce;
    }
}
