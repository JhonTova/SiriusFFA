package me.sirius.ffa.integration;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.arena.Bounds;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.logging.Level;

public final class WorldGuardHook {

    private final SiriusFFA plugin;
    private RegionBridge bridge;

    public WorldGuardHook(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    public void resolve() {
        Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) {
            return;
        }
        try {
            this.bridge = new WorldGuardBridge(plugin.getLogger());
            plugin.getLogger().info("WorldGuard detectado: las arenas se registrarán como regiones.");
        } catch (Throwable t) {
            // Una versión de WorldGuard con otra API no puede tumbar el plugin entero:
            // las arenas funcionan igual sin región, solo pierden esa integración.
            plugin.getLogger().log(Level.WARNING,
                    "WorldGuard está instalado pero su API no responde como se esperaba. "
                    + "Las arenas funcionarán sin región.", t);
            this.bridge = null;
        }
    }

    public boolean available() {
        return bridge != null;
    }

    public String regionName(Arena arena) {
        return (plugin.config().regionPrefix() + arena.id()).toLowerCase(Locale.ROOT);
    }

    public String apply(Arena arena) {
        Bounds bounds = arena.bounds();
        if (!available() || !plugin.config().createRegions() || bounds == null) {
            return "";
        }
        String id = regionName(arena);
        try {
            if (bridge.createOrUpdate(id, bounds, plugin.config().setPvpFlag())) {
                return id;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "No se pudo crear la región de " + arena.id(), t);
        }
        return "";
    }

    public void remove(Arena arena) {
        Bounds bounds = arena.bounds();
        if (!available() || bounds == null || arena.region().isBlank()) {
            return;
        }
        try {
            bridge.delete(bounds.world(), arena.region());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "No se pudo borrar la región de " + arena.id(), t);
        }
    }

    public void warnIfPvpDenied(Arena arena) {
        if (!available() || arena.spawn() == null) {
            return;
        }
        try {
            if (!bridge.pvpDenied(arena.spawn())) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        String region = arena.region().isBlank() ? regionName(arena) : arena.region();
        plugin.getLogger().warning("WorldGuard tiene el PVP DENEGADO en la arena '" + arena.id()
                + "', así que dentro no se podrán pegar. Arréglalo con: /rg flag " + region + " pvp allow");
    }

    public RegionBridge.Pvp closePvp(Arena arena) {
        Bounds bounds = arena.bounds();
        if (!available() || bounds == null || arena.region().isBlank()) {
            return null;
        }
        try {
            RegionBridge.Pvp previous = bridge.pvp(bounds.world(), arena.region());
            if (previous == RegionBridge.Pvp.DENY) {
                return null;
            }
            return bridge.pvp(bounds.world(), arena.region(), RegionBridge.Pvp.DENY) ? previous : null;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "No se pudo apagar el pvp de " + arena.id(), t);
            return null;
        }
    }

    public void restorePvp(Arena arena, RegionBridge.Pvp previous) {
        Bounds bounds = arena.bounds();
        if (previous == null || !available() || bounds == null || arena.region().isBlank()) {
            return;
        }
        try {
            bridge.pvp(bounds.world(), arena.region(), previous);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "No se pudo devolver el pvp de " + arena.id()
                    + " a su sitio. Revisa /rg flag " + arena.region() + " pvp", t);
        }
    }

    public boolean hasRegion(Arena arena) {
        Bounds bounds = arena.bounds();
        if (!available() || bounds == null || arena.region().isBlank()) {
            return false;
        }
        try {
            return bridge.exists(bounds.world(), arena.region());
        } catch (Throwable t) {
            return false;
        }
    }
}
