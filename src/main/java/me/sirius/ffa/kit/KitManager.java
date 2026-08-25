package me.sirius.ffa.kit;

import me.sirius.core.SiriusPlugin;
import me.sirius.core.config.Config;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KitManager {

    private final SiriusPlugin plugin;
    private final Config file;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    private final Map<UUID, String> editing = new ConcurrentHashMap<>();

    public KitManager(SiriusPlugin plugin) {
        this.plugin = plugin;
        this.file = new Config(plugin, "kits.yml");
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        kits.clear();
        ConfigurationSection root = file.section("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                kits.put(id.toLowerCase(), Kit.read(id.toLowerCase(), section));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("El kit '" + id + "' no se pudo leer: " + e.getMessage()
                        + ". Revísalo en kits.yml; el resto de kits siguen funcionando.");
            }
        }
    }

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(kits.keySet());
    }

    public int count() {
        return kits.size();
    }

    public Kit save(String id, Player from) {
        String key = id.toLowerCase();
        Kit kit = Kit.capture(key, from);

        ConfigurationSection section = file.raw().getConfigurationSection("kits." + key);
        if (section == null) {
            section = file.raw().createSection("kits." + key);
        }
        kit.write(section);

        kits.put(key, Kit.read(key, section));
        file.save();
        editing.remove(from.getUniqueId());
        return kits.get(key);
    }

    public boolean delete(String id) {
        String key = id.toLowerCase();
        if (kits.remove(key) == null) {
            return false;
        }
        file.set("kits." + key, null);
        file.save();
        return true;
    }

    public void startEditing(UUID player, String id) {
        editing.put(player, id.toLowerCase());
    }

    public String editingOf(UUID player) {
        return editing.get(player);
    }

    public void stopEditing(UUID player) {
        editing.remove(player);
    }

    public void clearEditing() {
        editing.clear();
    }
}
