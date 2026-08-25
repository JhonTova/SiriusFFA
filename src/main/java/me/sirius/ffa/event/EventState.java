package me.sirius.ffa.event;

public enum EventState {

    IDLE,
    WAITING,
    STARTING,
    ACTIVE,
    ENDING,
    FINISHED;

    public boolean acceptsPlayers() {
        return this == WAITING || this == STARTING;
    }

    public boolean running() {
        return this == WAITING || this == STARTING || this == ACTIVE;
    }
}
