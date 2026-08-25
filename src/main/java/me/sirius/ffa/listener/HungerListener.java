package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public final class HungerListener implements Listener {

    private final SiriusFFA plugin;

    public HungerListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session == null || !session.inEvent()) {
            return;
        }
        FFAEvent live = plugin.events().active();
        if (live == null || !live.has(player.getUniqueId())) {
            return;
        }
        if (!live.role(live.mode().role(player)).keepFood()) {
            return;
        }
        event.setCancelled(true);
        // Sin resetear el agotamiento el evento vuelve a dispararse cada pocos golpes.
        player.setSaturation(20F);
        player.setExhaustion(0F);
    }
}
