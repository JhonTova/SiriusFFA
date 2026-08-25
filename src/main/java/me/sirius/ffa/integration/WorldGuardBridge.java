package me.sirius.ffa.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.sirius.ffa.arena.Bounds;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.logging.Level;
import java.util.logging.Logger;

final class WorldGuardBridge implements RegionBridge {

    private final Logger logger;

    WorldGuardBridge(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean createOrUpdate(String id, Bounds bounds, boolean allowPvp) {
        RegionManager manager = manager(bounds.world());
        if (manager == null) {
            return false;
        }

        ProtectedRegion existing = manager.getRegion(id);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(id,
                BlockVector3.at(bounds.minX(), bounds.minY(), bounds.minZ()),
                BlockVector3.at(bounds.maxX(), bounds.maxY(), bounds.maxZ()));

        if (existing != null) {
            // Redefinir conserva lo que el administrador haya configurado a mano:
            // banderas, prioridad, miembros y dueños.
            region.copyFrom(existing);
        } else if (allowPvp) {
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        }

        manager.addRegion(region);
        save(manager);
        return true;
    }

    @Override
    public boolean delete(String world, String id) {
        RegionManager manager = manager(world);
        if (manager == null || manager.getRegion(id) == null) {
            return false;
        }
        manager.removeRegion(id);
        save(manager);
        return true;
    }

    @Override
    public boolean exists(String world, String id) {
        RegionManager manager = manager(world);
        return manager != null && manager.hasRegion(id);
    }

    @Override
    public boolean pvpDenied(org.bukkit.Location location) {
        if (location.getWorld() == null) {
            return false;
        }
        // Sujeto nulo = evaluación "global" de la bandera, que es la que se aplica a un
        // jugador cualquiera sin permisos especiales sobre la región.
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location))
                .queryState(null, Flags.PVP) == StateFlag.State.DENY;
    }

    @Override
    public Pvp pvp(String world, String id) {
        RegionManager manager = manager(world);
        ProtectedRegion region = manager == null ? null : manager.getRegion(id);
        if (region == null) {
            return Pvp.UNSET;
        }
        StateFlag.State state = region.getFlag(Flags.PVP);
        if (state == null) {
            return Pvp.UNSET;
        }
        return state == StateFlag.State.ALLOW ? Pvp.ALLOW : Pvp.DENY;
    }

    @Override
    public boolean pvp(String world, String id, Pvp state) {
        RegionManager manager = manager(world);
        ProtectedRegion region = manager == null ? null : manager.getRegion(id);
        if (region == null) {
            return false;
        }
        region.setFlag(Flags.PVP, switch (state) {
            case ALLOW -> StateFlag.State.ALLOW;
            case DENY -> StateFlag.State.DENY;
            case UNSET -> null;
        });
        save(manager);
        return true;
    }

    private RegionManager manager(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("El mundo '" + worldName + "' no está cargado; WorldGuard no puede tocar su región.");
            return null;
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
    }

    private void save(RegionManager manager) {
        try {
            manager.saveChanges();
        } catch (StorageException e) {
            logger.log(Level.WARNING, "WorldGuard no pudo guardar las regiones. "
                    + "El cambio está en memoria y se guardará con el resto.", e);
        }
    }
}
