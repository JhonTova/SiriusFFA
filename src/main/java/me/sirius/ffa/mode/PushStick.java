package me.sirius.ffa.mode;

import me.sirius.core.item.ItemBuilder;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PushStick {

    public static final String TAG = "ability";
    private static final String VALUE = "push";

    private static final int RING_POINTS = 24;

    private final SiriusFFA plugin;
    private final FFAEvent event;

    private final boolean enabled;
    private final Material material;
    private final int slot;
    private final int knockback;
    private final double radius;
    private final double strength;
    private final double lift;
    private final int cooldown;
    private final double damage;

    private final Map<UUID, Long> ready = new HashMap<>();

    private PushStick(FFAEvent event, ConfigurationSection section) {
        this.event = event;
        this.plugin = event.plugin();

        // Sin sección en la config la vara sale con sus valores de serie, no apagada: un
        // evento con un id propio ('jagger', 'juggernaut-navidad') no recibe los valores
        // por defecto del events.yml del jar, y apagarla ahí dejaba el rol sin nada que
        // hacer sin que nadie hubiera pedido quitársela. Para quitarla está
        // 'push.enabled: false', que sí es una decisión escrita.
        if (section != null && !section.getBoolean("enabled", true)) {
            this.enabled = false;
            this.material = Material.STICK;
            this.slot = 8;
            this.knockback = 0;
            this.radius = 0;
            this.strength = 0;
            this.lift = 0;
            this.cooldown = 0;
            this.damage = 0;
            return;
        }
        if (section == null) {
            this.enabled = true;
            this.material = Material.STICK;
            this.slot = 8;
            this.knockback = 2;
            this.radius = 5D;
            this.strength = 1.4D;
            this.lift = 0.42D;
            this.cooldown = 8;
            this.damage = 0D;
            return;
        }

        String name = section.getString("item", "STICK");
        Material chosen = name == null || name.isBlank() ? null : Material.matchMaterial(name);
        if (chosen == null || !chosen.isItem()) {
            chosen = Material.STICK;
            plugin.getLogger().warning("La vara de empuje de '" + event.definition().id()
                    + "' pide un material que no existe o que no se puede llevar en la mano: "
                    + "se usa STICK.");
        }

        this.enabled = true;
        this.material = chosen;
        this.slot = Math.clamp(section.getInt("slot", 8), 0, 8);
        this.knockback = Math.clamp(section.getInt("knockback", 2), 0, 10);
        this.radius = Math.max(1D, section.getDouble("radius", 5D));
        this.strength = Math.max(0.1D, section.getDouble("strength", 1.4D));
        this.lift = Math.clamp(section.getDouble("lift", 0.42D), 0D, 2D);
        this.cooldown = Math.max(0, section.getInt("cooldown", 8));
        this.damage = Math.max(0D, section.getDouble("damage", 0D));
    }

    public static PushStick read(FFAEvent event, ConfigurationSection section) {
        return new PushStick(event, section);
    }

    public boolean enabled() {
        return enabled;
    }

    public void give(Player player) {
        if (!enabled) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean repeated = take(player);
        ItemStack item = build();
        ItemStack occupied = inventory.getItem(slot);

        if (occupied == null || occupied.getType().isAir()) {
            inventory.setItem(slot, item);
        } else if (!inventory.addItem(item).isEmpty()) {
            inventory.setItem(slot, item);
        }
        player.updateInventory();
        if (!repeated) {
            plugin.messages().send(player, "juggernaut.push-ready", data());
        }
    }

    public boolean take(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean found = false;
        for (int i = 0; i < storage.length; i++) {
            if (isPushStick(storage[i])) {
                storage[i] = null;
                found = true;
            }
        }
        if (found) {
            inventory.setStorageContents(storage);
        }
        if (isPushStick(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
            found = true;
        }
        return found;
    }

    private ItemStack build() {
        ItemBuilder builder = ItemBuilder.of(material)
                .name(plugin.messages().get("juggernaut.push-name"), data())
                .lore(plugin.messages().lines("juggernaut.push-lore"), data())
                .tag(plugin, TAG, VALUE)
                .unbreakable()
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        if (knockback > 0) {
            builder.enchant(Enchantment.KNOCKBACK, knockback);
        }
        return builder.build();
    }

    private Map<String, String> data() {
        return Map.of(
                "cooldown", String.valueOf(cooldown),
                "radius", String.valueOf((int) radius));
    }

    public boolean isPushStick(ItemStack item) {
        return enabled && VALUE.equals(ItemBuilder.readTag(item, plugin, TAG));
    }

    public boolean use(Player player) {
        if (remaining(player.getUniqueId()) > 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.6F);
            return true;
        }
        if (cooldown > 0) {
            ready.put(player.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);
            player.setCooldown(material, cooldown * 20);
        }
        shove(player);
        show(player.getLocation());
        return true;
    }

    private void shove(Player source) {
        Location center = source.getLocation();
        double reach = radius * radius;

        for (Player target : event.players()) {
            if (target.getUniqueId().equals(source.getUniqueId())
                    || !target.getWorld().equals(source.getWorld())) {
                continue;
            }
            double distance = target.getLocation().distanceSquared(center);
            if (distance > reach) {
                continue;
            }
            FFASession session = plugin.sessions().of(target);
            if (session == null || session.spectator() || session.spawnProtected()) {
                continue;
            }
            target.setVelocity(away(center, target.getLocation())
                    .multiply(force(Math.sqrt(distance), radius, strength))
                    .setY(lift));
            target.playSound(target.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1F, 1.4F);

            if (damage > 0) {
                target.damage(damage, source);
            }
        }
    }

    private static Vector away(Location center, Location target) {
        Vector direction = target.toVector().subtract(center.toVector()).setY(0);
        if (direction.lengthSquared() < 1.0E-4) {
            // Uno encima de otro: no hay dirección que sacar de la distancia, así que se
            // usa hacia dónde mira el que empuja.
            direction = center.getDirection().setY(0);
        }
        return direction.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : direction.normalize();
    }

    static double force(double distance, double radius, double strength) {
        if (radius <= 0) {
            return strength;
        }
        return strength * (1D - Math.clamp(distance / radius, 0D, 1D) * 0.5D);
    }

    private void show(Location center) {
        center.getWorld().playSound(center, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2F, 0.9F);
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                center.clone().add(0, 1, 0), 3, 0.4, 0.3, 0.4, 0);

        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2 * Math.PI * i / RING_POINTS;
            center.getWorld().spawnParticle(Particle.CLOUD,
                    center.getX() + Math.cos(angle) * radius,
                    center.getY() + 0.4,
                    center.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    private int remaining(UUID player) {
        Long until = ready.get(player);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) Math.ceil(left / 1000D);
    }

    public void reset() {
        ready.clear();
    }
}
