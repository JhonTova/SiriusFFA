package me.sirius.ffa.event;

import me.sirius.core.config.Config;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.mode.FFAMode;
import me.sirius.ffa.session.LeaveReason;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventManager {

    public enum StartResult {
        OK(""),
        ALREADY_RUNNING("event.already-running"),
        NOT_FOUND("event.definition-not-found"),
        DISABLED("event.disabled"),
        ARENA_NOT_FOUND("event.arena-not-found"),
        ARENA_NOT_READY("event.arena-not-ready"),
        MODE_NOT_FOUND("event.mode-not-found");

        private final String message;

        StartResult(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public boolean ok() {
            return this == OK;
        }
    }

    private final SiriusFFA plugin;
    private final Config file;
    private final Map<String, EventDefinition> definitions = new LinkedHashMap<>();

    private FFAEvent active;

    public EventManager(SiriusFFA plugin) {
        this.plugin = plugin;
        this.file = new Config(plugin, "events.yml");
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        definitions.clear();
        ConfigurationSection root = file.section("events");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                definitions.put(id.toLowerCase(Locale.ROOT), EventDefinition.read(id, section));
            } catch (RuntimeException e) {
                plugin.getLogger().severe("El evento '" + id + "' no se pudo leer: " + e.getMessage());
            }
        }
    }

    public Collection<EventDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public EventDefinition definition(String id) {
        return id == null ? null : definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> ids() {
        return List.copyOf(definitions.keySet());
    }

    public FFAEvent active() {
        return active;
    }

    public boolean running() {
        return active != null && active.state().running();
    }

    public boolean joinable() {
        return active != null && active.state().acceptsPlayers();
    }

    public boolean locksArenas() {
        return active != null && plugin.config().closeArenasDuringEvents();
    }

    public StartResult start(String id) {
        if (active != null) {
            return StartResult.ALREADY_RUNNING;
        }
        EventDefinition definition = definition(id);
        if (definition == null) {
            return StartResult.NOT_FOUND;
        }
        if (!definition.enabled()) {
            return StartResult.DISABLED;
        }
        Arena arena = plugin.arenas().get(definition.arena());
        if (arena == null) {
            return StartResult.ARENA_NOT_FOUND;
        }
        if (!arena.isReady()) {
            return StartResult.ARENA_NOT_READY;
        }
        FFAMode.Factory factory = plugin.modes().factory(definition.mode());
        if (factory == null) {
            return StartResult.MODE_NOT_FOUND;
        }
        warnMissingKits(definition);

        this.active = new FFAEvent(plugin, definition, arena, factory);
        plugin.clock().ensureRunning();

        plugin.messages().broadcast("event.announced", Map.of(
                "event", definition.displayName(),
                "mode", definition.mode(),
                "arena", arena.displayName(),
                "min", String.valueOf(definition.minPlayers()),
                "max", definition.maxPlayers() > 0 ? String.valueOf(definition.maxPlayers()) : "-"));
        closeArenas();
        return StartResult.OK;
    }

    private void closeArenas() {
        if (!locksArenas()) {
            return;
        }
        plugin.messages().broadcast("event.arenas-closed", Map.of());

        String stage = active.arena().id();
        boolean all = plugin.config().ejectArenasOnEventStart();

        for (var session : plugin.sessions().all()) {
            if (session.inEvent() || (!all && !session.arena().equals(stage))) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.player());
            if (player != null) {
                plugin.messages().send(player, "event.arena-ejected");
                plugin.sessions().leave(player, LeaveReason.ADMIN);
            }
        }
    }

    public void warnOpenArenas() {
        for (EventDefinition definition : definitions.values()) {
            if (!definition.enabled()) {
                continue;
            }
            Arena arena = plugin.arenas().get(definition.arena());
            if (arena == null || arena.reserved()) {
                continue;
            }
            plugin.getLogger().warning("La arena '" + arena.id() + "' la usa el evento '"
                    + definition.id() + "' y sigue abierta al publico. Cierrala con "
                    + "/ffa privacy " + arena.id() + " closed para que no salga en /ffa join.");
        }
    }

    private void warnMissingKits(EventDefinition definition) {
        for (Map.Entry<String, String> entry : definition.kits().entrySet()) {
            if (!entry.getValue().isBlank() && !plugin.kits().exists(entry.getValue())) {
                plugin.getLogger().warning("El evento '" + definition.id() + "' usa el kit '"
                        + entry.getValue() + "' (" + entry.getKey() + ") y ese kit no existe. "
                        + "Los jugadores entrarán sin él.");
            }
        }
    }

    public boolean stop() {
        if (active == null) {
            return false;
        }
        active.cancel();
        return true;
    }

    public void shutdown() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    public void tick() {
        if (active == null) {
            return;
        }
        active.tick();

        if (active.state() == EventState.FINISHED) {
            active = null;
            if (plugin.config().closeArenasDuringEvents()) {
                plugin.messages().broadcast("event.arenas-open", Map.of());
            }
        }
    }
}
