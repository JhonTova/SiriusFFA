package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.arena.Bounds;
import me.sirius.ffa.config.FFAConfig;
import me.sirius.ffa.event.EventState;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class MovementListener implements Listener {

    private final SiriusFFA plugin;

    public MovementListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.sessions().empty() || !plugin.config().preventExit()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || !changedBlock(event.getFrom(), to)) {
            return;
        }
        Player player = event.getPlayer();
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }
        // Antes de empezar, los jugadores del evento esperan en la sala de espera, que
        // está fuera del cuboide: mientras tanto la frontera no aplica. Si el servidor no
        // ha configurado ninguna, esperan dentro y se sigue vigilando.
        if (session.inEvent()) {
            FFAEvent live = plugin.events().active();
            if (live == null) {
                return;
            }
            if (live.state() != EventState.ACTIVE && live.hasLobby()) {
                return;
            }
        }
        Arena arena = plugin.arenas().get(session.arena());
        Bounds bounds = arena == null ? null : arena.bounds();
        if (bounds == null || bounds.contains(to)) {
            return;
        }

        FFAConfig.BoundaryAction action = plugin.config().boundaryAction();
        switch (action) {
            case CANCEL -> event.setCancelled(true);
            case PUSH_BACK -> {
                Location clamped = bounds.clamp(to);
                clamped.setYaw(to.getYaw());
                clamped.setPitch(to.getPitch());
                plugin.sessions().teleport(player, clamped);
            }
            case TELEPORT -> plugin.sessions().teleport(player, arena.spawn());
        }
        plugin.messages().send(player, "protection.out-of-bounds");
    }

    private boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || from.getWorld() != to.getWorld();
    }
}
