package me.sirius.ffa.listener;

import me.sirius.core.gui.Menu;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.session.FFASession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class InventoryGuardListener implements Listener {

    private final SiriusFFA plugin;

    public InventoryGuardListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        FFASession session = plugin.sessions().of(event.getPlayer());
        if (session == null) {
            return;
        }
        if (session.spectator() || (session.protectedInventory() && plugin.config().blockDrops())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "protection.no-drop");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Los menús del propio plugin (y los de cualquier plugin de la suite) sí se abren:
        // son la GUI, no un sitio donde guardar cosas.
        if (holder instanceof Menu) {
            return;
        }
        if (session.spectator()) {
            event.setCancelled(true);
            return;
        }
        if (!session.protectedInventory() || !plugin.config().blockContainers()) {
            return;
        }
        InventoryType type = inventory.getType();
        if (type != InventoryType.PLAYER && type != InventoryType.CRAFTING) {
            event.setCancelled(true);
            plugin.messages().send(player, "protection.no-containers");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }
        if (session.spectator()) {
            event.setCancelled(true);
            return;
        }
        if (!session.protectedInventory() || !plugin.config().blockKitTransfer()) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Menu) {
            return;
        }
        // Con un inventario ajeno abierto no se mueve nada, ni con clic ni con shift:
        // el bloqueo de arriba no cubre las ventanas que abre otro plugin por código.
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
            plugin.messages().send(player, "protection.no-containers");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }
        if (session.spectator()) {
            event.setCancelled(true);
            return;
        }
        if (session.protectedInventory() && plugin.config().blockKitTransfer()
                && !(event.getInventory().getHolder() instanceof Menu)
                && event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session != null && session.spectator()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        FFASession session = plugin.sessions().of(event.getPlayer());
        if (session != null && session.spectator()) {
            event.setCancelled(true);
        }
    }
}
