package me.sirius.ffa.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStats {

    private final UUID player;
    private final Map<String, Long> values = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public PlayerStats(UUID player) {
        this.player = player;
    }

    public UUID player() {
        return player;
    }

    public long get(String stat) {
        return values.getOrDefault(stat, 0L);
    }

    public void add(String stat, long amount) {
        if (amount == 0) {
            return;
        }
        values.merge(stat, amount, Long::sum);
        dirty = true;
    }

    public void load(String stat, long value) {
        values.put(stat, value);
    }

    public void max(String stat, long value) {
        if (value > get(stat)) {
            values.put(stat, value);
            dirty = true;
        }
    }

    public boolean dirty() {
        return dirty;
    }

    public void clean() {
        dirty = false;
    }

    public Map<String, Long> snapshot() {
        return new HashMap<>(values);
    }

    public double ratio() {
        return Stat.ratio(get(Stat.KILLS), get(Stat.DEATHS));
    }
}
