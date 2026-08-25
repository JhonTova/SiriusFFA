package me.sirius.ffa.util;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class Players {

    private Players() {
    }

    public static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20D : max.getValue();
    }

    public static void heal(Player player) {
        player.setHealth(maxHealth(player));
    }

    public static void heal(Player player, double amount) {
        if (amount <= 0) {
            return;
        }
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
    }

    public static double clampHealth(Player player, double health) {
        return Math.clamp(health, 1D, maxHealth(player));
    }
}
