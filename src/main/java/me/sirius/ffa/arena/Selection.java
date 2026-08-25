package me.sirius.ffa.arena;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Selection {

    private final Map<UUID, Location> first = new ConcurrentHashMap<>();
    private final Map<UUID, Location> second = new ConcurrentHashMap<>();
    private final Map<UUID, String> editing = new ConcurrentHashMap<>();

    public void first(UUID player, Location location) {
        first.put(player, location.clone());
    }

    public void second(UUID player, Location location) {
        second.put(player, location.clone());
    }

    public Location first(UUID player) {
        return first.get(player);
    }

    public Location second(UUID player) {
        return second.get(player);
    }

    public boolean complete(UUID player) {
        return first.containsKey(player) && second.containsKey(player);
    }

    public Bounds bounds(UUID player) {
        Location a = first.get(player);
        Location b = second.get(player);
        if (a == null || b == null || a.getWorld() == null || a.getWorld() != b.getWorld()) {
            return null;
        }
        return Bounds.of(a, b);
    }

    public void editing(UUID player, String arena) {
        editing.put(player, arena.toLowerCase());
    }

    public String editing(UUID player) {
        return editing.get(player);
    }

    public void clear(UUID player) {
        first.remove(player);
        second.remove(player);
        editing.remove(player);
    }

    public void clear() {
        first.clear();
        second.clear();
        editing.clear();
    }
}
