package me.sirius.ffa.arena;

import org.bukkit.Location;

public record Bounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Bounds of(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null) {
            return null;
        }
        return new Bounds(
                first.getWorld().getName(),
                Math.min(first.getBlockX(), second.getBlockX()),
                Math.min(first.getBlockY(), second.getBlockY()),
                Math.min(first.getBlockZ(), second.getBlockZ()),
                Math.max(first.getBlockX(), second.getBlockX()),
                Math.max(first.getBlockY(), second.getBlockY()),
                Math.max(first.getBlockZ(), second.getBlockZ()));
    }

    public boolean contains(Location location) {
        return location != null
                && location.getWorld() != null
                && contains(location.getWorld().getName(),
                            location.getX(), location.getY(), location.getZ());
    }

    public boolean contains(String worldName, double x, double y, double z) {
        return world.equals(worldName)
                && x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    public Location clamp(Location location) {
        Location clamped = location.clone();
        clamped.setX(Math.clamp(location.getX(), minX + 0.5D, maxX + 0.5D));
        clamped.setY(Math.clamp(location.getY(), (double) minY, (double) maxY));
        clamped.setZ(Math.clamp(location.getZ(), minZ + 0.5D, maxZ + 0.5D));
        return clamped;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String encode() {
        return world + ";" + minX + ";" + minY + ";" + minZ + ";" + maxX + ";" + maxY + ";" + maxZ;
    }

    public static Bounds decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String[] parts = data.split(";");
        if (parts.length < 7) {
            return null;
        }
        try {
            return new Bounds(parts[0],
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return world + " (" + minX + "," + minY + "," + minZ + ") -> (" + maxX + "," + maxY + "," + maxZ + ")";
    }
}
