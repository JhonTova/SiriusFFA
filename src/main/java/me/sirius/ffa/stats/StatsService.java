package me.sirius.ffa.stats;

import me.sirius.core.scheduler.Scheduler;
import me.sirius.core.storage.Storage;
import me.sirius.ffa.SiriusFFA;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class StatsService {

    private static final String TABLE = "ffa_stats";

    private final SiriusFFA plugin;
    private final Storage storage;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    private Scheduler.Task flushTask;

    private final Map<String, List<Ranked>> topCache = new ConcurrentHashMap<>();
    private final Map<String, Long> topRefreshed = new ConcurrentHashMap<>();

    private static final long TOP_TTL = 60_000L;
    private static final int TOP_SIZE = 10;

    public StatsService(SiriusFFA plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    private boolean off() {
        return storage == null || !plugin.config().statsEnabled();
    }

    public void createSchema() {
        if (off()) {
            return;
        }
        storage.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "stat VARCHAR(48) NOT NULL,"
                + "value BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid, stat))");
    }

    public void start() {
        if (off()) {
            return;
        }
        int seconds = plugin.config().statsFlushSeconds();
        this.flushTask = plugin.scheduler().asyncTimer(this::flush, seconds * 20L, seconds * 20L);
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    public PlayerStats cached(UUID player) {
        return cache.get(player);
    }

    public void load(UUID player) {
        if (off() || cache.containsKey(player)) {
            return;
        }
        PlayerStats stats = new PlayerStats(player);
        cache.put(player, stats);

        plugin.scheduler().async(() -> {
            try {
                List<String[]> rows = storage.queryAll(
                        "SELECT stat, value FROM " + TABLE + " WHERE uuid = ?",
                        result -> new String[]{result.getString(1), String.valueOf(result.getLong(2))},
                        player.toString());
                for (String[] row : rows) {
                    stats.load(row[0], Long.parseLong(row[1]));
                }
                stats.clean();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudieron cargar las estadísticas de " + player, e);
            }
        });
    }

    public void unload(UUID player) {
        PlayerStats stats = cache.remove(player);
        if (off()) {
            return;
        }
        if (stats != null && stats.dirty()) {
            persist(List.of(stats));
        }
    }

    public void add(UUID player, String stat, long amount) {
        if (off()) {
            return;
        }
        PlayerStats stats = cache.get(player);
        if (stats != null) {
            stats.add(stat, amount);
        }
    }

    public void max(UUID player, String stat, long value) {
        if (off()) {
            return;
        }
        PlayerStats stats = cache.get(player);
        if (stats != null) {
            stats.max(stat, value);
        }
    }

    public void flush() {
        if (off()) {
            return;
        }
        List<PlayerStats> pending = new ArrayList<>();
        for (PlayerStats stats : cache.values()) {
            if (stats.dirty()) {
                pending.add(stats);
            }
        }
        if (!pending.isEmpty()) {
            persist(pending);
        }
    }

    public void flushNow() {
        if (off()) {
            cache.clear();
            return;
        }
        List<PlayerStats> pending = new ArrayList<>(cache.values());
        if (!pending.isEmpty()) {
            persist(pending);
        }
        cache.clear();
    }

    private void persist(List<PlayerStats> pending) {
        String sql = storage.isSqlite()
                ? "INSERT INTO " + TABLE + " (uuid, stat, value) VALUES (?, ?, ?) "
                  + "ON CONFLICT(uuid, stat) DO UPDATE SET value = excluded.value"
                : "INSERT INTO " + TABLE + " (uuid, stat, value) VALUES (?, ?, ?) "
                  + "ON DUPLICATE KEY UPDATE value = VALUES(value)";

        List<Object[]> rows = new ArrayList<>();
        for (PlayerStats stats : pending) {
            for (Map.Entry<String, Long> entry : stats.snapshot().entrySet()) {
                rows.add(new Object[]{stats.player().toString(), entry.getKey(), entry.getValue()});
            }
            stats.clean();
        }
        if (rows.isEmpty()) {
            return;
        }
        try {
            storage.batch(sql, rows);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron guardar las estadísticas", e);
        }
    }

    public List<Entry> top(String stat, int limit) {
        if (off()) {
            return List.of();
        }
        return storage.queryAll(
                "SELECT uuid, value FROM " + TABLE + " WHERE stat = ? ORDER BY value DESC LIMIT " + Math.max(1, limit),
                result -> new Entry(UUID.fromString(result.getString(1)), result.getLong(2)),
                stat);
    }

    public long lookup(UUID player, String stat) {
        if (off()) {
            return 0L;
        }
        Long value = storage.queryOne(
                "SELECT value FROM " + TABLE + " WHERE uuid = ? AND stat = ?",
                result -> result.getLong(1), player.toString(), stat);
        return value == null ? 0L : value;
    }

    public List<Ranked> cachedTop(String stat) {
        long now = System.currentTimeMillis();
        if (!off() && now - topRefreshed.getOrDefault(stat, 0L) > TOP_TTL) {
            // Se marca antes de lanzar el refresco: si no, diez placeholders seguidos
            // lanzarían diez consultas idénticas.
            topRefreshed.put(stat, now);
            plugin.scheduler().async(() -> refreshTop(stat));
        }
        return topCache.getOrDefault(stat, List.of());
    }

    private void refreshTop(String stat) {
        try {
            List<Entry> entries = top(stat, TOP_SIZE);
            List<Ranked> ranked = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.player());
                ranked.add(new Ranked(player.getName() == null ? "?" : player.getName(), entry.value()));
            }
            topCache.put(stat, List.copyOf(ranked));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo actualizar la clasificación de " + stat, e);
        }
    }

    public record Entry(UUID player, long value) {
    }

    public record Ranked(String name, long value) {
    }
}
