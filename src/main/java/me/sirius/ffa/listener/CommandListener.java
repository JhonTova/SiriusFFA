package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.command.CommandPolicy;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;

public final class CommandListener implements Listener {

    private final SiriusFFA plugin;

    public CommandListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }

        String command = canonical(event.getMessage());
        if (command.equals("ffa") || player.hasPermission("siriusffa.bypass.commands")) {
            return;
        }

        if (!policy(session).allows(command)) {
            event.setCancelled(true);
            plugin.messages().send(player, "commands.blocked", Map.of("command", command));
            return;
        }

        if (plugin.config().exitCommands().contains(command)) {
            session.exiting(plugin.config().exitWindow());
        }
    }

    private String canonical(String message) {
        String typed = CommandPolicy.normalize(message);
        PluginCommand command = Bukkit.getPluginCommand(typed);
        return command == null ? typed : command.getName().toLowerCase(java.util.Locale.ROOT);
    }

    private CommandPolicy policy(FFASession session) {
        if (session.inEvent()) {
            FFAEvent live = plugin.events().active();
            CommandPolicy own = live == null ? null : live.definition().commands();
            return first(own, plugin.config().eventPolicy(), plugin.config().globalPolicy());
        }

        Arena arena = plugin.arenas().get(session.arena());
        CommandPolicy own = arena == null ? null : arena.commands();
        CommandPolicy byType = arena != null && arena.kitEnabled()
                ? plugin.config().kitArenaPolicy()
                : plugin.config().noKitArenaPolicy();

        return first(own, byType, plugin.config().globalPolicy());
    }

    private CommandPolicy first(CommandPolicy... candidates) {
        for (CommandPolicy candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return CommandPolicy.ALLOW_ALL;
    }
}
