package me.sirius.ffa.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Participant {

    private final UUID player;
    private final String name;
    private final long joinedAt = System.currentTimeMillis();
    private final Map<String, Integer> counters = new HashMap<>();

    private int kills;
    private int deaths;
    private int streak;
    private boolean eliminated;

    public Participant(UUID player, String name) {
        this.player = player;
        this.name = name;
    }

    public UUID player() {
        return player;
    }

    public String name() {
        return name;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int streak() {
        return streak;
    }

    public void addKill() {
        kills++;
        streak++;
    }

    public void addDeath() {
        deaths++;
        streak = 0;
    }

    public boolean eliminated() {
        return eliminated;
    }

    public void eliminate() {
        this.eliminated = true;
    }

    public int counter(String key) {
        return counters.getOrDefault(key, 0);
    }

    public int counterOr(String key, int fallback) {
        return counters.getOrDefault(key, fallback);
    }

    public void setCounter(String key, int value) {
        counters.put(key, value);
    }

    public int addCounter(String key, int amount) {
        int value = counter(key) + amount;
        counters.put(key, value);
        return value;
    }
}
