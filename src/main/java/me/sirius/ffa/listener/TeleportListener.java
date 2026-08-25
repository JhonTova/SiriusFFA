package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.session.FFASession;
import me.sirius.ffa.session.LeaveReason;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TeleportListener implements Listener {

    private final SiriusFFA plugin;

    public TeleportListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }
        if (session.consumeTeleport()) {
            return;
        }

        Location destination = event.getTo();
        Arena arena = plugin.arenas().get(session.arena());
        boolean inside = arena != null && destination != null && arena.inside(destination);

        if (inside) {
            return;
        }
        if (session.exiting()) {
            plugin.sessions().leave(player, LeaveReason.EXIT_COMMAND);
            return;
        }
        if (plugin.config().allowsTeleport(event.getCause())) {
            return;
        }
        if (player.hasPermission("siriusffa.bypass.teleport")) {
            plugin.sessions().leave(player, LeaveReason.EXIT_COMMAND);
            return;
        }

        event.setCancelled(true);
        plugin.messages().send(player, "protection.no-teleport");
    }
}
