package me.sirius.ffa.session;

import me.sirius.core.scheduler.Scheduler;
import me.sirius.ffa.SiriusFFA;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class FFAClock {

    private final SiriusFFA plugin;
    private Scheduler.Task task;

    public FFAClock(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    public void ensureRunning() {
        if (task == null) {
            task = plugin.scheduler().syncTimer(this::tick, 20L, 20L);
        }
    }

    public void stop() {
        plugin.hud().bar().clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean running() {
        return task != null;
    }

    private void tick() {
        plugin.events().tick();
        plugin.hud().bar().update(plugin.events().active());

        for (FFASession session : plugin.sessions().all()) {
            Player player = Bukkit.getPlayer(session.player());
            if (player != null) {
                plugin.hud().update(player, session);
            }
        }

        if (plugin.sessions().empty() && plugin.events().active() == null) {
            stop();
        }
    }
}
