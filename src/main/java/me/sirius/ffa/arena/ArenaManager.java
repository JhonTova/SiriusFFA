package me.sirius.ffa.arena;

import me.sirius.core.SiriusPlugin;
import me.sirius.core.config.Config;
import me.sirius.ffa.util.Locations;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaManager {

    private final SiriusPlugin plugin;
    private final Config file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    private Location lobby;

    public ArenaManager(SiriusPlugin plugin) {
        this.plugin = plugin;
        this.file = new Config(plugin, "arenas.yml");
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        arenas.clear();
        this.lobby = Locations.decode(file.string("lobby", ""));

        ConfigurationSection root = file.section("arenas");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                arenas.put(id.toLowerCase(), Arena.read(id.toLowerCase(), section));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("La arena '" + id + "' no se pudo leer: " + e.getMessage());
            }
        }
    }

    public Arena get(String id) {
        return id == null ? null : arenas.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<Arena> all() {
        return List.copyOf(arenas.values());
    }

    public List<Arena> joinable() {
        List<Arena> out = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.enabled() && arena.isReady() && !arena.reserved()) {
                out.add(arena);
            }
        }
        return out;
    }

    public List<String> ids() {
        return List.copyOf(arenas.keySet());
    }

    public List<String> joinableIds() {
        List<String> out = new ArrayList<>();
        for (Arena arena : joinable()) {
            out.add(arena.id());
        }
        return out;
    }

    public Arena create(String id) {
        String key = id.toLowerCase();
        if (arenas.containsKey(key)) {
            return null;
        }
        Arena arena = new Arena(key);
        arenas.put(key, arena);
        save(arena);
        return arena;
    }

    public boolean delete(String id) {
        String key = id.toLowerCase();
        if (arenas.remove(key) == null) {
            return false;
        }
        file.set("arenas." + key, null);
        file.save();
        return true;
    }

    public void save(Arena arena) {
        ConfigurationSection section = file.raw().getConfigurationSection("arenas." + arena.id());
        if (section == null) {
            section = file.raw().createSection("arenas." + arena.id());
        }
        arena.write(section);
        file.save();
    }

    public Location lobby() {
        return lobby == null ? null : lobby.clone();
    }

    public void lobby(Location location) {
        this.lobby = location.clone();
        file.set("lobby", Locations.encode(location));
        file.save();
    }
}
