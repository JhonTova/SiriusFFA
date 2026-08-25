package me.sirius.ffa.integration;

import me.sirius.ffa.arena.Bounds;

public interface RegionBridge {

    enum Pvp { ALLOW, DENY, UNSET }

    boolean createOrUpdate(String id, Bounds bounds, boolean allowPvp);

    boolean delete(String world, String id);

    boolean exists(String world, String id);

    boolean pvpDenied(org.bukkit.Location location);

    Pvp pvp(String world, String id);

    boolean pvp(String world, String id, Pvp state);
}
