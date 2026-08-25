package me.sirius.ffa.kit;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class KitEffects {

    private static final int PERMANENT = 20 * 60 * 60 * 24;

    private KitEffects() {
    }

    public static PotionEffect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().toLowerCase().split(";");
        PotionEffectType type = resolve(parts[0]);
        if (type == null) {
            return null;
        }
        try {
            int seconds = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            int ticks = seconds <= 0 ? PERMANENT : seconds * 20;
            return new PotionEffect(type, ticks, Math.max(0, amplifier), false, false, true);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static PotionEffectType resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String clean = name.trim().toLowerCase().replace(' ', '_');
        NamespacedKey key = clean.indexOf(':') >= 0
                ? NamespacedKey.fromString(clean)
                : NamespacedKey.minecraft(clean);
        return key == null ? null : Registry.EFFECT.get(key);
    }
}
