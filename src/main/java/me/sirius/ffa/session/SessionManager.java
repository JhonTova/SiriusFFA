package me.sirius.ffa.session;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.inventory.PlayerSnapshot;
import me.sirius.ffa.kit.Kit;
import me.sirius.ffa.stats.Stat;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private final SiriusFFA plugin;

    private final Map<UUID, FFASession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byArena = new ConcurrentHashMap<>();

    private final Set<UUID> joining = ConcurrentHashMap.newKeySet();

    private final Map<UUID, Location> pendingReturn = new ConcurrentHashMap<>();

    public SessionManager(SiriusFFA plugin) {
        this.plugin = plugin;
    }


    public FFASession of(UUID player) {
        return sessions.get(player);
    }

    public FFASession of(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean inFFA(UUID player) {
        return sessions.containsKey(player);
    }

    public boolean empty() {
        return sessions.isEmpty();
    }

    public Collection<FFASession> all() {
        return List.copyOf(sessions.values());
    }

    public int countIn(String arena) {
        Set<UUID> players = byArena.get(arena);
        return players == null ? 0 : players.size();
    }

    public Set<UUID> playersIn(String arena) {
        Set<UUID> players = byArena.get(arena);
        return players == null ? Set.of() : Set.copyOf(players);
    }


    public JoinResult joinArena(Player player, Arena arena) {
        JoinResult check = validate(player, arena, arena.kitEnabled(),
                arena.requireEmptyInventory(), arena.kit());
        if (!check.ok()) {
            return check;
        }
        if (!arena.enabled()) {
            return JoinResult.ARENA_DISABLED;
        }
        if (!arena.isReady()) {
            return JoinResult.ARENA_NOT_READY;
        }
        if (arena.reserved()) {
            return JoinResult.ARENA_RESERVED;
        }
        if (plugin.events().locksArenas()) {
            return JoinResult.ARENAS_CLOSED;
        }
        if (arena.maxPlayers() > 0 && countIn(arena.id()) >= arena.maxPlayers()) {
            return JoinResult.ARENA_FULL;
        }

        begin(player, arena.kitEnabled(), () -> completeArena(player, arena));
        return JoinResult.OK;
    }


    public JoinResult joinEvent(Player player) {
        FFAEvent event = plugin.events().active();
        if (event == null) {
            return JoinResult.EVENT_NOT_RUNNING;
        }
        Arena arena = event.arena();
        boolean kit = event.usesKits();

        // Si el kit lo pone el modo —la escalera del gun game— no hay id que comprobar en
        // kits.yml: no existe en ningún fichero.
        JoinResult check = validate(player, arena, kit,
                kit && event.definition().requireEmptyInventory(),
                event.mode().usesKits() ? "" : event.definition().kit("normal"));
        if (!check.ok()) {
            return check;
        }
        JoinResult eventCheck = event.canJoin(player);
        if (!eventCheck.ok()) {
            return eventCheck;
        }

        begin(player, kit, () -> completeEvent(player, event));
        return JoinResult.OK;
    }

    private JoinResult validate(Player player, Arena arena, boolean kit,
                                boolean requireEmpty, String kitId) {
        FFASession current = sessions.get(player.getUniqueId());
        if (current != null) {
            return current.inEvent() ? JoinResult.ALREADY_IN_EVENT : JoinResult.ALREADY_IN_ARENA;
        }
        if (joining.contains(player.getUniqueId())) {
            return JoinResult.JOINING;
        }
        if (player.isDead()) {
            return JoinResult.DEAD;
        }
        if (arena == null || !arena.isReady()) {
            return JoinResult.ARENA_NOT_READY;
        }
        if (kit && !kitId.isBlank() && !plugin.kits().exists(kitId)) {
            return JoinResult.KIT_MISSING;
        }
        if (kit && requireEmpty
                && !plugin.inventories().isEmpty(player)
                && !player.hasPermission("siriusffa.bypass.inventory")) {
            return JoinResult.INVENTORY_NOT_EMPTY;
        }
        if (plugin.config().checkCombatOnJoin() && plugin.deluxeCombat().inCombat(player)) {
            return JoinResult.IN_COMBAT;
        }
        return JoinResult.OK;
    }

    private void begin(Player player, boolean kit, Runnable complete) {
        UUID uuid = player.getUniqueId();
        joining.add(uuid);

        plugin.inventories().protect(player, kit).thenAccept(saved -> plugin.scheduler().sync(() -> {
            joining.remove(uuid);

            if (!player.isOnline()) {
                // Se fue antes de tocarle nada: el respaldo describe el inventario que
                // ya tiene puesto, así que sobra.
                plugin.snapshots().discard(uuid);
                return;
            }
            if (!saved) {
                plugin.messages().send(player, JoinResult.BACKUP_FAILED.message());
                return;
            }
            if (sessions.containsKey(uuid)) {
                return;
            }
            complete.run();
        }));
    }

    private void completeArena(Player player, Arena arena) {
        FFASession session = new FFASession(player, SessionKind.ARENA, arena.id(), null, arena.kitEnabled());
        register(session);

        if (arena.kitEnabled()) {
            applyArenaKit(player, arena);
        }
        teleport(player, arena.spawn());
        protectSpawn(player, arena.spawnProtection());

        plugin.cloak().refresh(player);
        plugin.stats().add(player.getUniqueId(), Stat.ARENAS_JOINED, 1);
        plugin.messages().send(player, "join.arena", Map.of(
                "arena", arena.displayName(),
                "players", String.valueOf(countIn(arena.id()))));
        plugin.clock().ensureRunning();
    }

    private void completeEvent(Player player, FFAEvent event) {
        FFASession session = new FFASession(player, SessionKind.EVENT,
                event.arena().id(), event.definition().id(), event.usesKits());
        register(session);

        if (event.usesKits()) {
            plugin.inventories().prepare(player, true);
        }
        player.setGameMode(GameMode.SURVIVAL);

        // A la sala de espera, no al mapa: hasta que la cuenta atrás llegue a cero no se
        // pelea, y el kit se reparte al empezar.
        event.sendToLobby(player);

        event.add(player);
        plugin.cloak().refresh(player);
        plugin.messages().send(player, "join.event", event.data(Map.of()));
        plugin.clock().ensureRunning();
    }

    private void register(FFASession session) {
        sessions.put(session.player(), session);
        byArena.computeIfAbsent(session.arena(), key -> ConcurrentHashMap.newKeySet())
                .add(session.player());
    }

    private void applyArenaKit(Player player, Arena arena) {
        plugin.inventories().prepare(player, true);
        player.setGameMode(GameMode.SURVIVAL);

        Kit kit = plugin.kits().get(arena.kit());
        if (kit != null) {
            kit.apply(player);
            plugin.inventories().markLent(player);
        } else if (!arena.kit().isBlank()) {
            plugin.getLogger().warning("La arena '" + arena.id() + "' usa el kit '" + arena.kit()
                    + "', que no existe. El jugador entró sin kit.");
        }
    }


    public void leave(Player player, LeaveReason reason) {
        FFASession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            // Sin sesión no debería quedar nada pendiente, pero si quedara un respaldo el
            // jugador se estaría quedando con un kit prestado: se le devuelve igual.
            if (plugin.snapshots().has(player.getUniqueId())) {
                plugin.inventories().restorePending(player, reason.teleportBack());
            }
            return;
        }
        Set<UUID> arena = byArena.get(session.arena());
        if (arena != null) {
            arena.remove(player.getUniqueId());
            if (arena.isEmpty()) {
                byArena.remove(session.arena());
            }
        }

        FFAEvent event = plugin.events().active();
        if (session.inEvent() && event != null) {
            event.remove(player.getUniqueId());
        }

        plugin.cloak().clear(player);
        plugin.hud().clear(player, session);
        saveStats(session);

        PlayerSnapshot snapshot = plugin.snapshots().take(player.getUniqueId());
        if (snapshot != null) {
            // El viaje se hace aparte del inventario para poder aplazarlo si está muerto.
            plugin.inventories().restore(player, snapshot, false);
            sendBack(player, returnLocation(snapshot, reason));
        }

        // Última red: la devolución ya deja el inventario exacto, pero un ítem del kit que
        // llegara por otra vía —una muerte en el mismo tick, otro plugin reescribiendo el
        // inventario— se quedaría con él, y eso son objetos gratis en un survival.
        int lent = plugin.inventories().stripLent(player);
        if (lent > 0) {
            plugin.getLogger().warning("A " + player.getName() + " le quedaban " + lent
                    + " objeto(s) prestados del FFA al salir; se le han retirado.");
        }

        if (reason.announces()) {
            plugin.messages().send(player, session.inEvent() ? "leave.event" : "leave.arena", Map.of(
                    "arena", session.arena(),
                    "kills", String.valueOf(session.kills()),
                    "deaths", String.valueOf(session.deaths())));
        }
    }

    private Location returnLocation(PlayerSnapshot snapshot, LeaveReason reason) {
        if (!reason.teleportBack()) {
            return null;
        }
        if (plugin.config().returnMode() == me.sirius.ffa.config.FFAConfig.ReturnMode.LOBBY) {
            Location lobby = plugin.arenas().lobby();
            if (lobby != null) {
                return lobby;
            }
        }
        return snapshot.returnLocation();
    }

    private void sendBack(Player player, Location destination) {
        if (destination == null) {
            return;
        }
        if (!player.isDead()) {
            player.teleport(destination);
            return;
        }
        pendingReturn.put(player.getUniqueId(), destination);

        if (plugin.isEnabled()) {
            plugin.scheduler().syncLater(() -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
            }, plugin.config().respawnDelay());
        }
    }

    public Location consumeReturn(UUID player) {
        return pendingReturn.remove(player);
    }

    private void saveStats(FFASession session) {
        plugin.stats().add(session.player(), Stat.PLAYTIME, session.secondsPlayed());
        plugin.stats().max(session.player(), Stat.BEST_STREAK, session.bestStreak());
    }

    public void closeAll(LeaveReason reason) {
        for (FFASession session : List.copyOf(sessions.values())) {
            Player player = org.bukkit.Bukkit.getPlayer(session.player());
            if (player != null) {
                leave(player, reason);
            } else {
                sessions.remove(session.player());
            }
        }
        sessions.clear();
        byArena.clear();
        joining.clear();
        pendingReturn.clear();
    }


    public void teleport(Player player, Location destination) {
        if (destination == null) {
            return;
        }
        FFASession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.allowTeleport();
        }
        player.teleport(destination);
    }

    public void protectSpawn(Player player, int seconds) {
        FFASession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.protectSpawn(seconds > 0 ? seconds : plugin.config().defaultSpawnProtection());
    }

    public void makeSpectator(Player player) {
        FFASession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.spectator(true);
        plugin.inventories().prepare(player, true);
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void respawn(Player player) {
        FFASession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.spectator()) {
            // Se le puso en espectador estando muerto, y reaparecer le devuelve a
            // supervivencia: un eliminado tiene que volver a mirar, no a pelear.
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }
        reappear(player, session);

        // Morir borra todos los efectos, así que la invisibilidad se repone aquí. La
        // sesión se vuelve a buscar a propósito: reappear() puede haberla cerrado.
        plugin.cloak().refresh(player);
    }

    private void reappear(Player player, FFASession session) {
        FFAEvent event = plugin.events().active();

        if (session.inEvent() && event != null) {
            if (!event.state().running()) {
                // El evento ya está terminando: darle otro kit ahora sería dárselo para
                // quitárselo en cuanto le toque su viaje de vuelta.
                event.teleportToSpawn(player, null);
                return;
            }
            if (event.usesKits()) {
                event.equip(player);
            }
            event.mode().onRespawn(player);
            return;
        }

        Arena arena = plugin.arenas().get(session.arena());
        if (arena == null) {
            leave(player, LeaveReason.ADMIN);
            return;
        }
        if (arena.kitEnabled()) {
            applyArenaKit(player, arena);
        }
        Location spawn = arena.spawn();
        if (spawn == null) {
            plugin.getLogger().warning("La arena '" + arena.id() + "' no tiene punto de aparición: "
                    + player.getName() + " reaparecerá donde el mundo decida.");
            return;
        }
        teleport(player, spawn);
        protectSpawn(player, arena.spawnProtection());
    }

    public Location respawnLocation(FFASession session, Location deathLocation) {
        Arena arena = plugin.arenas().get(session.arena());
        return arena == null ? null : arena.spawnAwayFrom(deathLocation);
    }
}
