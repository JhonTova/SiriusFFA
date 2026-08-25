package me.sirius.ffa.util;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;

public final class Sounds {

    public record Cue(String key, float volume, float pitch) {
    }

    private Sounds() {
    }

    public static Cue parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().toLowerCase(Locale.ROOT).split(";");
        String key = parts[0].trim();
        if (key.isEmpty() || key.equals("none")) {
            return null;
        }
        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1F;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1F;
            return new Cue(key, Math.max(0F, volume), Math.clamp(pitch, 0.5F, 2F));
        } catch (NumberFormatException e) {
            return new Cue(key, 1F, 1F);
        }
    }

    public static void play(Player player, Cue cue) {
        if (player == null || cue == null) {
            return;
        }
        player.playSound(player.getLocation(), cue.key(), cue.volume(), cue.pitch());
    }

    public static void play(Collection<Player> players, Cue cue) {
        if (cue == null) {
            return;
        }
        for (Player player : players) {
            play(player, cue);
        }
    }
}
