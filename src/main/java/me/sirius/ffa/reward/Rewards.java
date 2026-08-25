package me.sirius.ffa.reward;

import me.sirius.core.SiriusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class Rewards {

    private final SiriusPlugin plugin;

    public Rewards(SiriusPlugin plugin) {
        this.plugin = plugin;
    }

    public void run(List<String> commands, Player player) {
        run(commands, player, null);
    }

    public void run(List<String> commands, Player player, Player other) {
        if (commands.isEmpty()) {
            return;
        }
        Map<String, String> values = Map.of(
                "player", player.getName(),
                "killer", player.getName(),
                "victim", other == null ? "" : other.getName());

        plugin.scheduler().sync(() -> {
            for (String raw : commands) {
                String command = apply(raw, values);
                if (command.isBlank()) {
                    continue;
                }
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Falló la recompensa: " + command, e);
                }
            }
        });
    }

    private static String apply(String raw, Map<String, String> values) {
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            command = command.replace("%" + entry.getKey() + "%", entry.getValue())
                    .replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return command.trim();
    }
}
