package me.sirius.ffa.kit;

import me.sirius.ffa.inventory.ItemCodec;
import me.sirius.ffa.util.Players;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public final class Kit {

    private final String id;
    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final List<PotionEffect> effects;
    private final boolean clearBefore;

    private Kit(String id, ItemStack[] storage, ItemStack[] armor, ItemStack offhand,
                List<PotionEffect> effects, boolean clearBefore) {
        this.id = id;
        this.storage = storage;
        this.armor = armor;
        this.offhand = offhand;
        this.effects = effects;
        this.clearBefore = clearBefore;
    }

    public static Kit of(String id, ItemStack[] storage, ItemStack[] armor) {
        return new Kit(id, storage, armor, null, List.of(), true);
    }

    public static Kit capture(String id, Player player) {
        PlayerInventory inventory = player.getInventory();
        return new Kit(id,
                ItemCodec.copy(inventory.getStorageContents()),
                ItemCodec.copy(inventory.getArmorContents()),
                inventory.getItemInOffHand().clone(),
                List.of(), true);
    }

    public static Kit read(String id, ConfigurationSection section) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String raw : section.getStringList("effects")) {
            PotionEffect effect = KitEffects.parse(raw);
            if (effect != null) {
                effects.add(effect);
            }
        }
        // Atajo para el caso de siempre: el juggernaut con el doble de vida. Un nivel de
        // health_boost son dos corazones extra, así que 'health-boost: 5' deja al
        // jugador con veinte.
        int boost = section.getInt("health-boost", 0);
        if (boost > 0) {
            PotionEffect extra = KitEffects.parse("health_boost;0;" + (boost - 1));
            if (extra != null) {
                effects.add(extra);
            }
        }

        if (written(section)) {
            return new Kit(id,
                    storage(section.getStringList("items")),
                    armor(section.getConfigurationSection("armor")),
                    KitItems.read(section.getString("offhand", "")),
                    List.copyOf(effects),
                    section.getBoolean("clear-before", true));
        }

        return new Kit(id,
                ItemCodec.decode(section.getString("contents", "")),
                ItemCodec.decode(section.getString("armor", "")),
                ItemCodec.decodeOne(section.getString("offhand", "")),
                List.copyOf(effects),
                section.getBoolean("clear-before", true));
    }

    private static boolean written(ConfigurationSection section) {
        return !section.contains("contents")
                && (section.isList("items") || section.isConfigurationSection("armor"));
    }

    private static ItemStack[] storage(List<String> lines) {
        ItemStack[] items = new ItemStack[36];
        for (String line : lines) {
            KitItems.Spec spec = KitItems.parse(line);
            ItemStack item = KitItems.build(spec);
            if (item == null) {
                continue;
            }
            int slot = spec.slot();
            if (slot < 0 || slot >= items.length || items[slot] != null) {
                slot = free(items);
            }
            if (slot >= 0) {
                items[slot] = item;
            }
        }
        return items;
    }

    private static int free(ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack[] armor(ConfigurationSection section) {
        ItemStack[] pieces = new ItemStack[4];
        if (section == null) {
            return pieces;
        }
        pieces[0] = KitItems.read(section.getString("boots", ""));
        pieces[1] = KitItems.read(section.getString("leggings", ""));
        pieces[2] = KitItems.read(section.getString("chestplate", ""));
        pieces[3] = KitItems.read(section.getString("helmet", ""));
        return pieces;
    }

    public void write(ConfigurationSection section) {
        // Un kit guardado desde el juego manda sobre lo que hubiera escrito a mano: si se
        // quedaran las dos formas, la lectura elegiría una y el administrador editaría la otra.
        section.set("items", null);
        section.set("offhand", null);
        section.set("contents", ItemCodec.encode(storage));
        section.set("armor", ItemCodec.encode(armor));
        section.set("offhand", ItemCodec.encodeOne(offhand));
        section.set("clear-before", clearBefore);
        if (!section.contains("effects")) {
            section.set("effects", List.of());
        }
        if (!section.contains("health-boost")) {
            section.set("health-boost", 0);
        }
    }

    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (clearBefore) {
            inventory.clear();
            inventory.setArmorContents(null);
            inventory.setItemInOffHand(null);
        }

        inventory.setStorageContents(ItemCodec.copy(fit(storage, inventory.getStorageContents().length)));
        inventory.setArmorContents(ItemCodec.copy(armor));
        inventory.setItemInOffHand(offhand == null ? null : offhand.clone());

        // Los efectos van antes de curar: si el kit sube la vida máxima, hay que llenar
        // la barra ya crecida y no la de veinte.
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        Players.heal(player);
        player.updateInventory();
    }

    private static ItemStack[] fit(ItemStack[] items, int size) {
        if (items.length == size) {
            return items;
        }
        ItemStack[] out = new ItemStack[size];
        System.arraycopy(items, 0, out, 0, Math.min(items.length, size));
        return out;
    }

    public String id() {
        return id;
    }

    public int itemCount() {
        int count = 0;
        for (ItemStack item : storage) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        for (ItemStack item : armor) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return offhand != null && !offhand.getType().isAir() ? count + 1 : count;
    }

    public boolean isEmpty() {
        return itemCount() == 0;
    }

    public ItemStack preview() {
        for (ItemStack item : storage) {
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        for (ItemStack item : armor) {
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return null;
    }
}
