package me.sirius.ffa.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Locations {

    private Locations() {
    }

    public static String encode(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%s;%.2f;%.2f;%.2f;%.1f;%.1f",
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public static Location decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String[] parts = data.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String worldOf(String data) {
        if (data == null || data.isBlank()) {
            return "";
        }
        int separator = data.indexOf(';');
        return separator < 0 ? data : data.substring(0, separator);
    }
}
