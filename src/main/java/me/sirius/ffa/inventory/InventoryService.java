package me.sirius.ffa.inventory;

import me.sirius.core.SiriusPlugin;
import me.sirius.ffa.util.Players;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class InventoryService {

    private final SiriusPlugin plugin;
    private final SnapshotStore store;

    private final NamespacedKey lent;

    public InventoryService(SiriusPlugin plugin, SnapshotStore store) {
        this.plugin = plugin;
        this.store = store;
        this.lent = new NamespacedKey(plugin, "prestado");
    }

    public SnapshotStore store() {
        return store;
    }

    public boolean isEmpty(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        return offhand.getType().isAir();
    }

    public CompletableFuture<Boolean> protect(Player player, boolean restoreInventory) {
        return store.save(PlayerSnapshot.capture(player, restoreInventory));
    }

    public void prepare(Player player, boolean heal) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        player.setItemOnCursor(null);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0);

        if (heal) {
            // A tope de la vida que tenga, sin tocar su máximo: si otro plugin le dio
            // corazones extra, son suyos y siguen ahí.
            Players.heal(player);
            player.setFoodLevel(20);
            player.setSaturation(10F);
            player.setExhaustion(0F);
        }
    }

    public void restore(Player player, PlayerSnapshot snapshot, boolean teleport) {
        if (snapshot.restoreInventory()) {
            revive(player, snapshot);
        }
        if (teleport) {
            Location back = snapshot.returnLocation();
            if (back != null) {
                player.teleport(back);
            }
        }
        player.updateInventory();
    }

    private void revive(Player player, PlayerSnapshot snapshot) {
        prepare(player, false);

        PlayerInventory inventory = player.getInventory();
        try {
            inventory.setStorageContents(sized(ItemCodec.decode(snapshot.contents()),
                    inventory.getStorageContents().length));
            inventory.setArmorContents(ItemCodec.decode(snapshot.armor()));
            inventory.setItemInOffHand(ItemCodec.decodeOne(snapshot.offhand()));
        } catch (ItemCodec.CodecException e) {
            // El respaldo se conserva en disco: prefiero un jugador sin ítems y un
            // fichero recuperable a mano que un fichero borrado y un ticket perdido.
            plugin.getLogger().log(Level.SEVERE, "No se pudo devolver el inventario de "
                    + snapshot.name() + ". El respaldo sigue en "
                    + store.folder() + "/" + snapshot.player() + ".yml", e);
        }

        // La vida se devuelve contra el máximo que el jugador tenga ahora, que es el
        // mismo de siempre: aquí nunca se cambió.
        player.setHealth(Players.clampHealth(player, snapshot.health()));
        player.setFoodLevel(snapshot.food());
        player.setSaturation(snapshot.saturation());
        player.setExhaustion(snapshot.exhaustion());
        player.setLevel(snapshot.experienceLevel());
        player.setExp(snapshot.experience());
        player.setRemainingAir(snapshot.remainingAir());
        player.setFireTicks(snapshot.fireTicks());
        player.setGameMode(snapshot.gameMode());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying() && snapshot.allowFlight());

        for (PotionEffect effect : snapshot.potionEffects()) {
            player.addPotionEffect(effect);
        }
    }

    public boolean restorePending(Player player, boolean teleport) {
        PlayerSnapshot snapshot = store.take(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }
        restore(player, snapshot, teleport);
        plugin.getLogger().info("Inventario de " + player.getName()
                + " recuperado tras un cierre inesperado.");
        return true;
    }

    private static ItemStack[] sized(ItemStack[] items, int size) {
        if (items.length == size) {
            return items;
        }
        ItemStack[] out = new ItemStack[size];
        System.arraycopy(items, 0, out, 0, Math.min(items.length, size));
        return out;
    }

    public void stripKit(Player player) {
        prepare(player, false);
        player.setGameMode(GameMode.SURVIVAL);
    }

    public void markLent(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (ItemStack item : storage) {
            mark(item);
        }
        inventory.setStorageContents(storage);

        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            mark(item);
        }
        inventory.setArmorContents(armor);

        ItemStack offhand = inventory.getItemInOffHand();
        if (mark(offhand)) {
            inventory.setItemInOffHand(offhand);
        }
    }

    public int stripLent(Player player) {
        PlayerInventory inventory = player.getInventory();
        int removed = 0;

        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (isLent(storage[i])) {
                storage[i] = null;
                removed++;
            }
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (isLent(armor[i])) {
                armor[i] = null;
                removed++;
            }
        }
        if (removed > 0) {
            inventory.setStorageContents(storage);
            inventory.setArmorContents(armor);
        }
        if (isLent(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
            removed++;
        }
        if (isLent(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        if (removed > 0) {
            player.updateInventory();
        }
        return removed;
    }

    public ItemStack markLent(ItemStack item) {
        mark(item);
        return item;
    }

    public boolean isLent(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(lent, PersistentDataType.BYTE);
    }

    private boolean mark(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(lent, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return true;
    }
}
