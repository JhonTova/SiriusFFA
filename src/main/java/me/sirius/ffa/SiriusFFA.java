package me.sirius.ffa;

import me.sirius.core.SiriusPlugin;
import me.sirius.core.storage.Storage;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.arena.ArenaManager;
import me.sirius.ffa.arena.Selection;
import me.sirius.ffa.command.FFACommand;
import me.sirius.ffa.config.FFAConfig;
import me.sirius.ffa.config.Messages;
import me.sirius.ffa.event.EventManager;
import me.sirius.ffa.event.EventScheduler;
import me.sirius.ffa.hud.Hud;
import me.sirius.ffa.integration.DeluxeCombatHook;
import me.sirius.ffa.integration.FFAExpansion;
import me.sirius.ffa.integration.WorldGuardHook;
import me.sirius.ffa.inventory.InventoryService;
import me.sirius.ffa.inventory.SnapshotStore;
import me.sirius.ffa.kit.KitManager;
import me.sirius.ffa.listener.AbilityListener;
import me.sirius.ffa.listener.CombatListener;
import me.sirius.ffa.listener.CommandListener;
import me.sirius.ffa.listener.ConnectionListener;
import me.sirius.ffa.listener.HungerListener;
import me.sirius.ffa.listener.InventoryGuardListener;
import me.sirius.ffa.listener.MovementListener;
import me.sirius.ffa.listener.TeleportListener;
import me.sirius.ffa.mode.ModeRegistry;
import me.sirius.ffa.reward.Rewards;
import me.sirius.ffa.session.FFAClock;
import me.sirius.ffa.session.LeaveReason;
import me.sirius.ffa.session.SessionManager;
import me.sirius.ffa.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class SiriusFFA extends SiriusPlugin {

    private FFAConfig config;
    private Messages messages;

    private SnapshotStore snapshots;
    private InventoryService inventories;

    private ArenaManager arenas;
    private KitManager kits;
    private ModeRegistry modes;
    private EventManager events;
    private EventScheduler schedules;
    private SessionManager sessions;

    private Storage storage;
    private StatsService stats;

    private FFAClock clock;
    private Hud hud;
    private Rewards rewards;
    private Selection selection;

    private WorldGuardHook worldGuard;
    private DeluxeCombatHook deluxeCombat;

    @Override
    protected void onStart() {
        this.config = new FFAConfig(this);
        this.messages = new Messages(this);

        // Lo primero de todo: recuperar lo que quedó a medias. Si el servidor se cayó con
        // gente dentro, sus inventarios tienen que estar en memoria antes de que puedan
        // volver a conectarse.
        this.snapshots = new SnapshotStore(this);
        int pending = snapshots.loadPending();
        if (pending > 0) {
            getLogger().info(pending + " inventario(s) pendientes de devolver: se restaurarán "
                    + "en cuanto esos jugadores vuelvan a entrar.");
        }
        this.inventories = new InventoryService(this, snapshots);

        this.arenas = new ArenaManager(this);
        this.kits = new KitManager(this);
        this.modes = new ModeRegistry();
        this.events = new EventManager(this);
        this.schedules = new EventScheduler(this);
        this.sessions = new SessionManager(this);
        this.clock = new FFAClock(this);
        this.hud = new Hud(this);
        this.rewards = new Rewards(this);
        this.selection = new Selection();

        this.worldGuard = new WorldGuardHook(this);
        this.deluxeCombat = new DeluxeCombatHook(this);

        if (config.statsEnabled()) {
            this.storage = Storage.from(config.file(), getDataFolder(), "siriusffa");
            this.stats = new StatsService(this, storage);
            stats.createSchema();
            stats.start();
        } else {
            this.stats = new StatsService(this, null);
        }

        listeners(
                new ConnectionListener(this),
                new CombatListener(this),
                new AbilityListener(this),
                new InventoryGuardListener(this),
                new HungerListener(this),
                new CommandListener(this),
                new TeleportListener(this),
                new MovementListener(this));

        command("ffa", new FFACommand(this));

        getLogger().info(arenas.all().size() + " arena(s), " + kits.count() + " kit(s) y "
                + events.definitions().size() + " evento(s) cargados.");
    }

    @Override
    protected void onServerReady() {
        worldGuard.resolve();
        deluxeCombat.resolve();

        if (hooks().hasPlaceholderApi()) {
            new FFAExpansion(this).register();
        }

        restoreRegions();
        events.warnOpenArenas();

        // El programador arranca aquí y no en onStart() para que su primer disparo cuente
        // desde que el servidor está de verdad en marcha.
        schedules.start();

        // Un /reload en caliente deja gente conectada que nunca pasó por el evento de
        // entrada: sus estadísticas y sus respaldos se atienden aquí.
        for (Player player : Bukkit.getOnlinePlayers()) {
            stats.load(player.getUniqueId());
            if (snapshots.has(player.getUniqueId()) && !sessions.inFFA(player.getUniqueId())) {
                inventories.restorePending(player, true);
            }
        }
    }

    private void restoreRegions() {
        if (!worldGuard.available() || !config.createRegions()) {
            return;
        }
        for (Arena arena : arenas.all()) {
            if (arena.bounds() == null) {
                continue;
            }
            if (worldGuard.hasRegion(arena)) {
                worldGuard.warnIfPvpDenied(arena);
                continue;
            }
            String region = worldGuard.apply(arena);
            if (region.isBlank()) {
                continue;
            }
            arena.region(region);
            arenas.save(arena);
            getLogger().info("Región '" + region + "' creada para la arena '" + arena.id()
                    + "'. Sus flags se ajustan con /rg flag " + region + " ...");
            worldGuard.warnIfPvpDenied(arena);
        }
    }

    @Override
    protected void onStop() {
        // El orden importa: primero se cierra el evento, que saca a sus jugadores por la
        // vía normal, y después se barre lo que quede en arenas. Todo lo que no se pueda
        // devolver ahora sigue en disco y se devuelve al reconectar.
        if (schedules != null) {
            schedules.stop();
        }
        if (events != null) {
            events.shutdown();
        }
        if (sessions != null) {
            sessions.closeAll(LeaveReason.SHUTDOWN);
        }
        if (clock != null) {
            clock.stop();
        }
        if (stats != null) {
            stats.stop();
            stats.flushNow();
        }
        if (storage != null) {
            storage.close();
        }
        if (kits != null) {
            kits.clearEditing();
        }
        if (selection != null) {
            selection.clear();
        }
    }

    public void reloadAll() {
        config.reload();
        messages.reload();
        arenas.reload();
        kits.reload();
        events.reload();
        schedules.reload();
        events.warnOpenArenas();
    }

    public FFAConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public SnapshotStore snapshots() {
        return snapshots;
    }

    public InventoryService inventories() {
        return inventories;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public KitManager kits() {
        return kits;
    }

    public ModeRegistry modes() {
        return modes;
    }

    public EventManager events() {
        return events;
    }

    public EventScheduler schedules() {
        return schedules;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public StatsService stats() {
        return stats;
    }

    public FFAClock clock() {
        return clock;
    }

    public Hud hud() {
        return hud;
    }

    public Rewards rewards() {
        return rewards;
    }

    public Selection selection() {
        return selection;
    }

    public WorldGuardHook worldGuard() {
        return worldGuard;
    }

    public DeluxeCombatHook deluxeCombat() {
        return deluxeCombat;
    }
}
