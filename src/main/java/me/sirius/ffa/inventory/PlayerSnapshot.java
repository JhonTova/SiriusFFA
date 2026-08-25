package me.sirius.ffa.inventory;

import me.sirius.ffa.util.Locations;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PlayerSnapshot(
        UUID player,
        String name,
        long createdAt,
        boolean restoreInventory,
        String contents,
        String armor,
        String offhand,
        List<String> effects,
        String location,
        GameMode gameMode,
        double health,
        int food,
        float saturation,
        float exhaustion,
        int fireTicks,
        boolean allowFlight,
        boolean flying,
        int experienceLevel,
        float experience,
        int remainingAir
) {

    public static PlayerSnapshot capture(Player player, boolean restoreInventory) {
        PlayerInventory inventory = player.getInventory();

        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(encodeEffect(effect));
        }

        return new PlayerSnapshot(
                player.getUniqueId(),
                player.getName(),
                System.currentTimeMillis(),
                restoreInventory,
                restoreInventory ? ItemCodec.encode(inventory.getStorageContents()) : "",
                restoreInventory ? ItemCodec.encode(inventory.getArmorContents()) : "",
                restoreInventory ? ItemCodec.encodeOne(inventory.getItemInOffHand()) : "",
                effects,
                Locations.encode(player.getLocation()),
                player.getGameMode(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getFireTicks(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getLevel(),
                player.getExp(),
                player.getRemainingAir());
    }

    public Location returnLocation() {
        return Locations.decode(location);
    }

    public List<PotionEffect> potionEffects() {
        List<PotionEffect> out = new ArrayList<>(effects.size());
        for (String raw : effects) {
            PotionEffect effect = decodeEffect(raw);
            if (effect != null) {
                out.add(effect);
            }
        }
        return out;
    }

    public void write(ConfigurationSection section) {
        section.set("player", player.toString());
        section.set("name", name);
        section.set("created-at", createdAt);
        section.set("restore-inventory", restoreInventory);
        section.set("contents", contents);
        section.set("armor", armor);
        section.set("offhand", offhand);
        section.set("effects", effects);
        section.set("location", location);
        section.set("gamemode", gameMode.name());
        section.set("health", health);
        section.set("food", food);
        section.set("saturation", saturation);
        section.set("exhaustion", exhaustion);
        section.set("fire-ticks", fireTicks);
        section.set("allow-flight", allowFlight);
        section.set("flying", flying);
        section.set("experience-level", experienceLevel);
        section.set("experience", experience);
        section.set("remaining-air", remainingAir);
    }

    public static PlayerSnapshot read(ConfigurationSection section) {
        String rawUuid = section.getString("player", "");
        if (rawUuid.isBlank()) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }

        GameMode mode;
        try {
            mode = GameMode.valueOf(section.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException e) {
            mode = GameMode.SURVIVAL;
        }

        return new PlayerSnapshot(
                uuid,
                section.getString("name", ""),
                section.getLong("created-at"),
                section.getBoolean("restore-inventory", true),
                section.getString("contents", ""),
                section.getString("armor", ""),
                section.getString("offhand", ""),
                section.getStringList("effects"),
                section.getString("location", ""),
                mode,
                section.getDouble("health", 20D),
                section.getInt("food", 20),
                (float) section.getDouble("saturation", 5D),
                (float) section.getDouble("exhaustion", 0D),
                section.getInt("fire-ticks", 0),
                section.getBoolean("allow-flight", false),
                section.getBoolean("flying", false),
                section.getInt("experience-level", 0),
                (float) section.getDouble("experience", 0D),
                section.getInt("remaining-air", 300));
    }

    @SuppressWarnings("deprecation")
    private static String encodeEffect(PotionEffect effect) {
        return effect.getType().getKey() + ";" + effect.getDuration() + ";" + effect.getAmplifier()
                + ";" + effect.isAmbient() + ";" + effect.hasParticles() + ";" + effect.hasIcon();
    }

    private static PotionEffect decodeEffect(String raw) {
        String[] parts = raw.split(";");
        if (parts.length < 3) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(parts[0]);
        PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
        if (type == null) {
            return null;
        }
        try {
            return new PotionEffect(type,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    parts.length > 3 && Boolean.parseBoolean(parts[3]),
                    parts.length <= 4 || Boolean.parseBoolean(parts[4]),
                    parts.length <= 5 || Boolean.parseBoolean(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
