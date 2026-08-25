package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.event.EventState;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class AbilityListener implements Listener {

    private final SiriusFFA plugin;

    public AbilityListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Un mismo clic llega una vez por mano; sin esto la onda saldría doble y se
        // comería su propia espera.
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        Player player = event.getPlayer();
        FFASession session = plugin.sessions().of(player);
        if (session == null || !session.inEvent() || session.spectator()) {
            return;
        }
        FFAEvent live = plugin.events().active();
        if (live == null || live.state() != EventState.ACTIVE || !live.has(player.getUniqueId())) {
            return;
        }
        if (live.mode().onUse(player, item)) {
            event.setCancelled(true);
        }
    }
}
