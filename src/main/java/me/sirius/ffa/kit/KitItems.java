package me.sirius.ffa.kit;

import me.sirius.core.text.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class KitItems {

    public record Spec(String material, int amount, int slot,
                       Map<String, Integer> enchantments, String name) {
    }

    private KitItems() {
    }

    public static Spec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";", -1);
        String material = parts[0].trim().toLowerCase(Locale.ROOT);
        if (material.isEmpty()) {
            return null;
        }
        int amount = number(parts, 1, 1);
        int slot = number(parts, 2, -1);

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        if (parts.length > 3) {
            for (String entry : parts[3].split(",")) {
                String clean = entry.trim().toLowerCase(Locale.ROOT);
                if (clean.isEmpty()) {
                    continue;
                }
                int colon = clean.indexOf(':');
                if (colon < 0) {
                    enchantments.put(clean, 1);
                    continue;
                }
                try {
                    enchantments.put(clean.substring(0, colon),
                            Integer.parseInt(clean.substring(colon + 1).trim()));
                } catch (NumberFormatException e) {
                    enchantments.put(clean.substring(0, colon), 1);
                }
            }
        }
        String name = parts.length > 4 ? parts[4].trim() : "";
        return new Spec(material, Math.clamp(amount, 1, 64), slot, enchantments, name);
    }

    public static ItemStack build(Spec spec) {
        if (spec == null) {
            return null;
        }
        Material material = Material.matchMaterial(spec.material());
        if (material == null || !material.isItem()) {
            return null;
        }
        ItemStack item = new ItemStack(material, spec.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (!spec.name().isBlank()) {
            meta.setDisplayName(Text.legacy(spec.name()));
        }
        for (Map.Entry<String, Integer> entry : spec.enchantments().entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
            if (enchantment != null) {
                meta.addEnchant(enchantment, Math.max(1, entry.getValue()), true);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack read(String raw) {
        return build(parse(raw));
    }

    private static Enchantment enchantment(String name) {
        String clean = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        NamespacedKey key = clean.indexOf(':') >= 0
                ? NamespacedKey.fromString(clean)
                : NamespacedKey.minecraft(clean);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private static int number(String[] parts, int index, int fallback) {
        if (parts.length <= index || parts[index].isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
