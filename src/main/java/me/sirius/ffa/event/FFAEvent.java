package me.sirius.ffa.event;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.integration.RegionBridge;
import me.sirius.ffa.kit.Kit;
import me.sirius.ffa.mode.FFAMode;
import me.sirius.ffa.session.JoinResult;
import me.sirius.ffa.session.LeaveReason;
import me.sirius.ffa.stats.Stat;
import me.sirius.ffa.util.Durations;
import me.sirius.ffa.util.Players;
import me.sirius.ffa.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FFAEvent {

    private final SiriusFFA plugin;
    private final EventDefinition definition;
    private final Arena arena;
    private final FFAMode mode;

    private final Map<UUID, Participant> participants = new LinkedHashMap<>();

    private final Set<String> warnedKits = new HashSet<>();

    private EventState state = EventState.WAITING;
    private int countdown;
    private int waiting;
    private int elapsed;
    private int endTimer;
    private int round;
    private long startedAt;

    private UUID winner;
    private String winnerName = "";

    private RegionBridge.Pvp previousPvp;

    public FFAEvent(SiriusFFA plugin, EventDefinition definition, Arena arena, FFAMode.Factory factory) {
        this.plugin = plugin;
        this.definition = definition;
        this.arena = arena;
        this.countdown = definition.countdown();
        this.mode = factory.create(this);
    }


    public SiriusFFA plugin() {
        return plugin;
    }

    public EventDefinition definition() {
        return definition;
    }

    public Arena arena() {
        return arena;
    }

    public FFAMode mode() {
        return mode;
    }

    public boolean usesKits() {
        return definition.usesKits() || mode.usesKits();
    }

    public EventState state() {
        return state;
    }

    public int round() {
        return round;
    }

    public int nextRound() {
        return ++round;
    }

    public int elapsed() {
        return elapsed;
    }

    public int countdown() {
        return countdown;
    }

    public int endTimer() {
        return endTimer;
    }

    public UUID winner() {
        return winner;
    }

    public String winnerName() {
        return winnerName;
    }


    public Participant participant(UUID player) {
        return participants.get(player);
    }

    public boolean has(UUID player) {
        return participants.containsKey(player);
    }

    public Collection<Participant> participants() {
        return List.copyOf(participants.values());
    }

    public int size() {
        return participants.size();
    }

    public List<Participant> alive() {
        List<Participant> out = new ArrayList<>();
        for (Participant participant : participants.values()) {
            if (!participant.eliminated() && Bukkit.getPlayer(participant.player()) != null) {
                out.add(participant);
            }
        }
        return out;
    }

    public List<Player> players() {
        List<Player> out = new ArrayList<>(participants.size());
        for (UUID uuid : participants.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    public UUID leader() {
        Participant best = null;
        for (Participant participant : participants.values()) {
            if (best == null || participant.kills() > best.kills()) {
                best = participant;
            }
        }
        return best == null ? null : best.player();
    }

    public JoinResult canJoin(Player player) {
        if (!state.acceptsPlayers()) {
            return JoinResult.EVENT_CLOSED;
        }
        if (definition.maxPlayers() > 0 && participants.size() >= definition.maxPlayers()) {
            return JoinResult.EVENT_FULL;
        }
        if (participants.containsKey(player.getUniqueId())) {
            return JoinResult.ALREADY_IN_EVENT;
        }
        return JoinResult.OK;
    }

    public void add(Player player) {
        participants.put(player.getUniqueId(), new Participant(player.getUniqueId(), player.getName()));
        plugin.stats().add(player.getUniqueId(), Stat.EVENTS_PLAYED, 1);

        broadcast("event.player-joined", Map.of(
                "player", player.getName(),
                "players", String.valueOf(participants.size()),
                "min", String.valueOf(definition.minPlayers())));

        mode.onJoin(player);
    }

    public void remove(UUID player) {
        Participant gone = participants.remove(player);
        if (gone == null) {
            return;
        }
        // Con el evento terminando, el modo no debe reaccionar: rotar el juggernaut o
        // repartir un kit ahora se lo daría a alguien a quien ya se le devolvió lo suyo.
        if (state.running()) {
            mode.onQuit(player);
        }

        // Quedarse solo es ganar. Se mira a los que siguen compitiendo, no a los que
        // hay en la sala: en un evento con espectadores los eliminados siguen dentro.
        List<Participant> rest = alive();
        if (state == EventState.ACTIVE && rest.size() <= 1) {
            mode.onLastStanding(rest.isEmpty() ? null : rest.get(0).player());
        }
    }


    public void tick() {
        switch (state) {
            case WAITING -> tickWaiting();
            case STARTING -> tickStarting();
            case ACTIVE -> tickActive();
            case ENDING -> tickEnding();
            default -> { }
        }
    }

    private void tickWaiting() {
        waiting++;
        if (participants.size() >= definition.minPlayers()) {
            state = EventState.STARTING;
            countdown = definition.countdown();
            broadcastServer("event.countdown-started", data(Map.of(
                    "seconds", String.valueOf(countdown))));
            return;
        }

        int timeout = definition.setting("wait-timeout", 180);
        if (timeout > 0 && waiting >= timeout) {
            broadcastServer("event.not-enough-players", data(Map.of(
                    "players", String.valueOf(participants.size()),
                    "min", String.valueOf(definition.minPlayers()))));
            cancel();
        }
    }

    private void tickStarting() {
        if (participants.size() < definition.minPlayers()) {
            state = EventState.WAITING;
            countdown = definition.countdown();
            broadcast("event.countdown-aborted", data(Map.of()));
            return;
        }
        if (countdown <= 0) {
            begin();
            return;
        }
        if (countdown <= 5 || countdown == 10 || countdown == 15 || countdown == 30 || countdown == 60) {
            Map<String, String> values = data(Map.of("seconds", String.valueOf(countdown)));
            for (Player player : players()) {
                plugin.messages().send(player, "event.countdown", values);
                plugin.messages().title(player, "event.countdown-title", "event.countdown-subtitle", values);
            }
        }
        countdown--;
    }

    private void tickActive() {
        elapsed++;
        mode.onSecond();

        if (state != EventState.ACTIVE) {
            return;
        }
        if (definition.maxTime() > 0 && elapsed >= definition.maxTime()) {
            mode.onTimeUp();
        }
    }

    private void tickEnding() {
        if (--endTimer <= 0) {
            close();
            return;
        }
        if (endTimer == 30 || endTimer == 20 || endTimer == 10 || endTimer <= 5) {
            broadcast("event.return-countdown", data(Map.of(
                    "seconds", String.valueOf(endTimer))));
        }
    }


    private void begin() {
        state = EventState.ACTIVE;
        startedAt = System.currentTimeMillis();
        elapsed = 0;
        round = 0;

        // Aquí es donde la sala de espera se vacía: a todos al mapa, con su kit puesto.
        for (Player player : players()) {
            equip(player);
        }
        mode.onStart();

        plugin.getLogger().info("Evento '" + definition.id() + "' en juego con "
                + participants.size() + " jugador(es), modo " + mode.id() + ".");
        plugin.worldGuard().warnIfPvpDenied(arena);

        Map<String, String> values = data(Map.of("players", String.valueOf(participants.size())));
        for (Player player : players()) {
            plugin.messages().send(player, "event.started", values);
            plugin.messages().title(player, "event.started-title", "event.started-subtitle", values);
        }
    }

    public void equip(Player player) {
        if (player.isDead()) {
            // A un muerto no se le puede teletransportar ni curar, y el kit se le
            // quedaría a medias. Se le equipa al reaparecer, que es lo que hace la
            // sesión un tick después de su respawn.
            return;
        }
        if (usesKits()) {
            // Un evento sin kits no toca el inventario de nadie: la gente pelea con lo
            // suyo, no se le ha respaldado nada y marcarlo como prestado se lo borraría
            // al salir.
            plugin.inventories().prepare(player, true);
            dress(player);
        }
        player.setGameMode(GameMode.SURVIVAL);
        teleportToSpawn(player, null);
    }

    public void redress(Player player) {
        if (player.isDead() || !usesKits()) {
            return;
        }
        plugin.inventories().prepare(player, false);
        dress(player);
    }

    private void dress(Player player) {
        String name = mode.role(player);

        Kit kit = mode.kit(player);
        if (kit != null) {
            kit.apply(player);
        } else {
            warnMissingKit(player, name);
        }
        role(name).apply(player);

        mode.onEquip(player);
        plugin.inventories().markLent(player);
    }

    public EventDefinition.Role role(String name) {
        EventDefinition.Role written = definition.role(name);
        return written.isEmpty() ? mode.defaultRole(name) : written;
    }

    private void warnMissingKit(Player player, String role) {
        if (!warnedKits.add(role)) {
            return;
        }
        String id = mode.kitFor(player);
        if (id == null || id.isBlank()) {
            plugin.getLogger().warning("El evento '" + definition.id() + "' no tiene kit para el rol '"
                    + role + "': esos jugadores pelearán con las manos vacías. Escríbelo en "
                    + "events.yml -> " + definition.id() + " -> kits -> " + role + ".");
            return;
        }
        plugin.getLogger().warning("El evento '" + definition.id() + "' usa el kit '" + id
                + "' para el rol '" + role + "', y ese kit no existe en kits.yml: esos jugadores "
                + "pelearán con las manos vacías.");
    }

    public Location lobby() {
        Location own = arena.lobby();
        if (own != null) {
            return own;
        }
        Location global = plugin.arenas().lobby();
        return global != null ? global : arena.spawn();
    }

    public void sendToLobby(Player player) {
        plugin.sessions().teleport(player, lobby());
    }

    public boolean hasLobby() {
        return arena.lobby() != null || plugin.arenas().lobby() != null;
    }

    public void teleportToSpawn(Player player, Location from) {
        Location spawn = from == null ? arena.spawn() : arena.spawnAwayFrom(from);
        if (spawn == null) {
            return;
        }
        plugin.sessions().teleport(player, spawn);
        plugin.sessions().protectSpawn(player, arena.spawnProtection());
    }

    public void eliminate(UUID uuid) {
        Participant participant = participants.get(uuid);
        if (participant == null || participant.eliminated()) {
            return;
        }
        participant.eliminate();

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        if (definition.spectators() && plugin.config().spectatorEnabled()) {
            plugin.sessions().makeSpectator(player);
            plugin.messages().send(player, "event.eliminated-spectator", data(Map.of()));
        } else {
            plugin.messages().send(player, "event.eliminated", data(Map.of()));
            plugin.sessions().leave(player, LeaveReason.ELIMINATED);
        }
    }

    public void finish(UUID winner) {
        if (state == EventState.ENDING || state == EventState.FINISHED) {
            return;
        }
        this.winner = winner;

        Participant champion = winner == null ? null : participants.get(winner);
        this.winnerName = champion == null ? plugin.messages().get("event.no-winner") : champion.name();

        beginEnding();
        mode.onEnd();
        award();
        announceResult();
        announceReturn();
    }

    public void cancel() {
        if (state == EventState.FINISHED) {
            return;
        }
        if (state == EventState.ENDING) {
            endTimer = 1;
            return;
        }
        this.winnerName = "";
        beginEnding();
        mode.onEnd();
        broadcast("event.cancelled", data(Map.of("seconds", String.valueOf(endTimer))));
        announceReturn();
    }

    private void beginEnding() {
        boolean played = state == EventState.ACTIVE;
        state = EventState.ENDING;
        endTimer = played ? Math.max(1, definition.endDelay()) : 1;

        if (!played) {
            return;
        }
        if (plugin.config().pvpOffOnEnd()) {
            previousPvp = plugin.worldGuard().closePvp(arena);
        }
        // Con el evento decidido, nadie debería morirse esperando el viaje de vuelta.
        for (Player player : players()) {
            player.setFireTicks(0);
            player.setFallDistance(0);
            Players.heal(player);
        }
    }

    private void announceReturn() {
        if (endTimer > 1) {
            broadcast("event.pvp-off", data(Map.of("seconds", String.valueOf(endTimer))));
        }
    }

    private void award() {
        for (Participant participant : participants.values()) {
            Player player = Bukkit.getPlayer(participant.player());
            if (player == null) {
                continue;
            }
            boolean champion = participant.player().equals(winner);
            plugin.stats().add(participant.player(),
                    champion ? Stat.EVENT_WINS : Stat.EVENT_LOSSES, 1);

            if (champion && !definition.winnerCommands().isEmpty()) {
                plugin.rewards().run(definition.winnerCommands(), player);
            }
            if (!definition.participationCommands().isEmpty()) {
                plugin.rewards().run(definition.participationCommands(), player);
            }
        }
    }

    private void announceResult() {
        boolean nobody = winner == null;
        String suffix = nobody ? "-none" : "";

        Map<String, String> values = data(Map.of(
                "winner", winnerName,
                "rounds", String.valueOf(Math.max(1, round)),
                "duration", Durations.words(elapsed),
                "players", String.valueOf(participants.size())));

        plugin.messages().broadcast("event.finished-broadcast" + suffix, values);
        for (Player player : players()) {
            plugin.messages().sendLines(player, "event.summary" + suffix, values);
            plugin.messages().title(player, "event.finished-title" + suffix,
                    "event.finished-subtitle" + suffix, values);
            Sounds.play(player, plugin.config().cue(
                    player.getUniqueId().equals(winner) ? "event-win" : "event-end"));
        }
    }

    public void close() {
        state = EventState.FINISHED;
        plugin.worldGuard().restorePvp(arena, previousPvp);
        previousPvp = null;

        for (UUID uuid : List.copyOf(participants.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.sessions().leave(player, LeaveReason.EVENT_END);
            } else {
                participants.remove(uuid);
            }
        }
        participants.clear();

        // Barrido de seguridad: cualquier sesión que siga apuntando a este evento se
        // cierra aunque haya perdido su sitio en la lista. Nadie puede quedarse dentro
        // de la arena con un kit prestado porque un contador se descuadrara.
        for (var session : plugin.sessions().all()) {
            if (!session.inEvent() || !definition.id().equals(session.event())) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.player());
            if (player != null) {
                plugin.getLogger().warning("La sesión de " + player.getName() + " seguía abierta al "
                        + "cerrar el evento; se le devuelve su inventario igualmente.");
                plugin.sessions().leave(player, LeaveReason.EVENT_END);
            }
        }
    }


    public void broadcast(String key, Map<String, String> values) {
        for (Player player : players()) {
            plugin.messages().send(player, key, values);
        }
    }

    public void broadcastTitle(String titleKey, String subtitleKey, Map<String, String> values) {
        for (Player player : players()) {
            plugin.messages().title(player, titleKey, subtitleKey, values);
        }
    }

    public void sound(String cue) {
        Sounds.play(players(), plugin.config().cue(cue));
    }

    public void broadcastServer(String key, Map<String, String> values) {
        plugin.messages().broadcast(key, values);
    }

    public Map<String, String> data(Map<String, String> extra) {
        Map<String, String> values = new HashMap<>(extra);
        values.put("event", definition.displayName());
        values.put("mode", mode.displayName());
        values.put("arena", arena.displayName());
        values.put("round", String.valueOf(Math.max(1, round)));
        values.put("time", Durations.clock(elapsed));
        values.putIfAbsent("remaining", definition.maxTime() > 0
                ? Durations.clock(Math.max(0, definition.maxTime() - elapsed))
                : "-");
        values.putIfAbsent("players", String.valueOf(participants.size()));
        return values;
    }

    public long startedAt() {
        return startedAt;
    }
}
