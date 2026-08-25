package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.session.FFASession;
import me.sirius.ffa.session.LeaveReason;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

public final class ConnectionListener implements Listener {

    private final SiriusFFA plugin;

    public ConnectionListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.stats().load(player.getUniqueId());

        if (!plugin.snapshots().has(player.getUniqueId())) {
            return;
        }
        // Margen para que los plugins que también escriben el inventario al entrar
        // terminen antes que nosotros. Un tick basta para casi todos; con un
        // sincronizador entre servidores como HuskSync hay que subir
        // 'general.recovery-delay-ticks' hasta después de su carga, o su escritura
        // pisaría la nuestra.
        plugin.scheduler().syncLater(() -> {
            if (!player.isOnline() || plugin.sessions().inFFA(player.getUniqueId())) {
                return;
            }
            if (plugin.inventories().restorePending(player, true)) {
                plugin.messages().send(player, "inventory.recovered");
            }
        }, plugin.config().recoveryDelay());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.kits().stopEditing(player.getUniqueId());
        plugin.selection().clear(player.getUniqueId());

        FFASession session = plugin.sessions().of(player);
        if (session != null && removes(session)) {
            // El inventario se devuelve dentro de este evento a propósito: Bukkit guarda
            // los datos del jugador justo después, así que lo que se le reponga aquí es
            // lo que tendrá al volver.
            plugin.sessions().leave(player, LeaveReason.QUIT);
            announce(session);
        }
        plugin.stats().unload(player.getUniqueId());
    }

    private boolean removes(FFASession session) {
        return session.inEvent()
                ? plugin.config().removeFromEventOnQuit()
                : plugin.config().removeFromArenaOnQuit();
    }

    private void announce(FFASession session) {
        if (!session.inEvent() || plugin.events().active() == null) {
            return;
        }
        plugin.events().active().broadcast("event.player-left", Map.of(
                "player", session.playerName(),
                "players", String.valueOf(plugin.events().active().size())));
    }
}
