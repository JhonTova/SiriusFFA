package me.sirius.ffa.integration;

import me.sirius.ffa.SiriusFFA;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class DeluxeCombatHook {

    private final SiriusFFA plugin;
    private boolean present;

    public DeluxeCombatHook(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    public void resolve() {
        Plugin combat = Bukkit.getPluginManager().getPlugin("DeluxeCombat");
        this.present = combat != null && combat.isEnabled();

        if (!present || !plugin.config().deluxeCombatEnabled()) {
            return;
        }
        if (!plugin.hooks().hasPlaceholderApi()) {
            plugin.getLogger().warning("DeluxeCombat está instalado pero falta PlaceholderAPI, "
                    + "así que no se puede saber quién está en combate. "
                    + "Instálalo o desactiva 'deluxecombat.enabled' para dejar de ver este aviso.");
            return;
        }
        plugin.getLogger().info("DeluxeCombat detectado: se respetará su estado de combate.");
    }

    public boolean available() {
        return present && plugin.config().deluxeCombatEnabled() && plugin.hooks().hasPlaceholderApi();
    }

    public boolean inCombat(Player player) {
        if (!available()) {
            return false;
        }
        String placeholder = plugin.config().combatPlaceholder();
        if (placeholder.isBlank()) {
            return false;
        }
        String value = plugin.hooks().placeholders(player, placeholder);
        if (value == null || value.isBlank() || value.equals(placeholder)) {
            return false;
        }
        return plugin.config().combatTrueValues().contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
