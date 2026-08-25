package me.sirius.ffa.command;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.arena.ArenaAccess;
import me.sirius.ffa.arena.Bounds;
import me.sirius.ffa.event.EventManager;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.gui.FFAMenu;
import me.sirius.ffa.kit.Kit;
import me.sirius.ffa.session.FFASession;
import me.sirius.ffa.session.JoinResult;
import me.sirius.ffa.session.LeaveReason;
import me.sirius.ffa.stats.PlayerStats;
import me.sirius.ffa.stats.Stat;
import me.sirius.ffa.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FFACommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS =
            List.of("join", "leave", "list", "info", "stats", "top", "next", "help");

    private static final List<String> ADMIN_SUBS =
            List.of("start", "stop", "schedule", "create", "delete", "bounds", "pos1", "pos2",
                    "select", "setspawn", "addspawn", "clearspawns", "setlobby", "setkit",
                    "privacy", "kitcreate", "kitsave", "kitdelete", "kitlist", "reload");

    private final SiriusFFA plugin;

    public FFACommand(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                FFAMenu.open(plugin, player);
            } else {
                help(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join", "entrar" -> join(sender, args);
            case "leave", "salir" -> leave(sender);
            case "list", "lista" -> list(sender);
            case "info" -> info(sender, args);
            case "stats", "estadisticas" -> stats(sender, args);
            case "top" -> top(sender, args);
            case "next", "proximo" -> next(sender);

            case "start", "iniciar" -> start(sender, args);
            case "stop", "parar" -> stop(sender);
            case "schedule", "programador" -> schedule(sender, args);
            case "create", "crear" -> create(sender, args);
            case "delete", "borrar" -> delete(sender, args);
            case "bounds", "limites" -> bounds(sender, args);
            case "select", "seleccionar" -> select(sender, args);
            case "pos1" -> position(sender, true);
            case "pos2" -> position(sender, false);
            case "setspawn" -> setSpawn(sender, args, true);
            case "addspawn" -> setSpawn(sender, args, false);
            case "clearspawns" -> clearSpawns(sender, args);
            case "setlobby" -> setLobby(sender, args);
            case "setkit" -> setKit(sender, args);
            case "privacy", "privacity", "privacidad" -> privacy(sender, args);
            case "kitcreate" -> kitCreate(sender, args);
            case "kitsave" -> kitSave(sender, args);
            case "kitdelete" -> kitDelete(sender, args);
            case "kitlist" -> kitList(sender);
            case "reload", "recargar" -> reload(sender);
            default -> help(sender);
        }
        return true;
    }


    private void join(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.join")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "commands.usage-join");
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);

        if (target.equals("event") || target.equals("evento")) {
            report(player, plugin.sessions().joinEvent(player), Map.of());
            return;
        }
        Arena arena = plugin.arenas().get(target);
        if (arena == null) {
            plugin.messages().send(player, "arena.not-found", Map.of("arena", target));
            return;
        }
        report(player, plugin.sessions().joinArena(player, arena), Map.of("arena", arena.displayName()));
    }

    private void report(Player player, JoinResult result, Map<String, String> data) {
        if (!result.ok()) {
            plugin.messages().send(player, result.message(), data);
        }
    }

    private void leave(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.leave")) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            plugin.messages().send(player, "leave.not-in-ffa");
            return;
        }
        if (session.inEvent()) {
            FFAEvent event = plugin.events().active();
            if (event != null && !event.definition().allowLeave()
                    && !player.hasPermission("siriusffa.admin")) {
                plugin.messages().send(player, "leave.not-allowed");
                return;
            }
        } else if (!plugin.config().allowLeaveArena()) {
            plugin.messages().send(player, "leave.not-allowed");
            return;
        }
        plugin.sessions().leave(player, LeaveReason.COMMAND);
    }

    private void list(CommandSender sender) {
        plugin.messages().send(sender, "arena.list-header");
        for (Arena arena : plugin.arenas().all()) {
            plugin.messages().sendRich(sender, "arena.list-line", Map.of(
                    "arena", arena.displayName(),
                    "id", arena.id(),
                    "players", String.valueOf(plugin.sessions().countIn(arena.id())),
                    "max", arena.maxPlayers() > 0 ? String.valueOf(arena.maxPlayers()) : "-",
                    "kit", arena.kitEnabled() ? arena.kit() : "-",
                    "state", plugin.messages().get(state(arena))));
        }
        FFAEvent event = plugin.events().active();
        if (event != null) {
            plugin.messages().sendRich(sender, "event.list-line", event.data(Map.of(
                    "state", plugin.messages().get("state." + event.state().name().toLowerCase()))));
        }
    }

    private String state(Arena arena) {
        if (!arena.enabled()) {
            return "state.disabled";
        }
        if (!arena.isReady()) {
            return "state.incomplete";
        }
        if (arena.reserved()) {
            return "state.reserved";
        }
        return plugin.events().locksArenas() ? "state.event-lock" : "state.open";
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage-info");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        Bounds bounds = arena.bounds();
        Location lobby = arena.lobby() != null ? arena.lobby() : plugin.arenas().lobby();

        plugin.messages().sendRichLines(sender, "arena.info", Map.of(
                "arena", arena.displayName(),
                "id", arena.id(),
                "kit", arena.kitEnabled() ? arena.kit() : "-",
                "spawns", String.valueOf(arena.spawns().size()),
                "bounds", bounds == null ? "-" : bounds.toString(),
                "region", arena.region().isBlank() ? "-" : arena.region(),
                "lobby", lobby == null ? "-" : lobby.getBlockX() + " " + lobby.getBlockY()
                        + " " + lobby.getBlockZ(),
                "players", String.valueOf(plugin.sessions().countIn(arena.id())),
                "max", arena.maxPlayers() > 0 ? String.valueOf(arena.maxPlayers()) : "-",
                "state", plugin.messages().get(state(arena))));
    }

    @SuppressWarnings("deprecation")
    private void stats(CommandSender sender, String[] args) {
        String name = args.length > 1 ? args[1] : sender.getName();

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            PlayerStats cached = plugin.stats().cached(online.getUniqueId());
            if (cached != null) {
                show(sender, online.getName(), cached.snapshot());
                return;
            }
        }

        // Resolver a un jugador que no está conectado puede tocar disco o red, así que
        // eso y la consulta de estadísticas van juntas fuera del hilo principal.
        UUID known = online == null ? null : online.getUniqueId();
        plugin.scheduler().async(() -> {
            UUID uuid = known;
            String display = name;
            if (uuid == null) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    plugin.scheduler().sync(() -> plugin.messages().send(sender,
                            "general.player-not-found", Map.of("player", name)));
                    return;
                }
                uuid = target.getUniqueId();
                display = target.getName() == null ? name : target.getName();
            }

            Map<String, Long> values = new HashMap<>();
            for (String stat : Stat.PROFILE) {
                values.put(stat, plugin.stats().lookup(uuid, stat));
            }
            String shown = display;
            plugin.scheduler().sync(() -> show(sender, shown, values));
        });
    }

    private void show(CommandSender sender, String name, Map<String, Long> values) {
        long kills = values.getOrDefault(Stat.KILLS, 0L);
        long deaths = values.getOrDefault(Stat.DEATHS, 0L);

        plugin.messages().sendLines(sender, "stats.profile", Map.of(
                "player", name,
                "kills", String.valueOf(kills),
                "deaths", String.valueOf(deaths),
                "ratio", String.format(Locale.ROOT, "%.2f", Stat.ratio(kills, deaths)),
                "streak", String.valueOf(values.getOrDefault(Stat.BEST_STREAK, 0L)),
                "events", String.valueOf(values.getOrDefault(Stat.EVENTS_PLAYED, 0L)),
                "wins", String.valueOf(values.getOrDefault(Stat.EVENT_WINS, 0L)),
                "juggernaut", String.valueOf(values.getOrDefault(Stat.JUGGERNAUT_WINS, 0L)),
                "playtime", Durations.words((int) (long) values.getOrDefault(Stat.PLAYTIME, 0L))));
    }

    private void top(CommandSender sender, String[] args) {
        String stat = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : Stat.KILLS;

        plugin.scheduler().async(() -> {
            List<me.sirius.ffa.stats.StatsService.Entry> entries = plugin.stats().top(stat, 10);
            plugin.scheduler().sync(() -> {
                plugin.messages().send(sender, "stats.top-header", Map.of("stat", stat));
                int position = 1;
                for (var entry : entries) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(entry.player());
                    plugin.messages().send(sender, "stats.top-line", Map.of(
                            "position", String.valueOf(position++),
                            "player", player.getName() == null ? "?" : player.getName(),
                            "value", String.valueOf(entry.value())));
                }
                if (entries.isEmpty()) {
                    plugin.messages().send(sender, "stats.top-empty");
                }
            });
        });
    }


    private void start(CommandSender sender, String[] args) {
        if (!allowed(sender, "siriusffa.start")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage-start", Map.of(
                    "events", String.join(", ", plugin.events().ids())));
            return;
        }
        EventManager.StartResult result = plugin.events().start(args[1]);
        if (!result.ok()) {
            plugin.messages().send(sender, result.message(), Map.of("event", args[1]));
            return;
        }
        plugin.messages().send(sender, "event.started-admin", Map.of("event", args[1]));
    }

    private void stop(CommandSender sender) {
        if (!allowed(sender, "siriusffa.stop")) {
            return;
        }
        if (!plugin.events().stop()) {
            plugin.messages().send(sender, "event.not-running");
            return;
        }
        plugin.messages().send(sender, "event.stopped-admin");
    }

    private void next(CommandSender sender) {
        int seconds = plugin.schedules().secondsUntilNext();
        if (seconds < 0) {
            plugin.messages().send(sender, "scheduler.disabled");
            return;
        }
        String upcoming = plugin.schedules().nextEvent();
        plugin.messages().send(sender, "scheduler.next", Map.of(
                "time", Durations.words(seconds),
                "event", upcoming.isBlank() ? plugin.messages().get("scheduler.random") : upcoming));
    }

    private void schedule(CommandSender sender, String[] args) {
        if (!allowed(sender, "siriusffa.event")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage-schedule");
            return;
        }
        if (!plugin.schedules().enabled()) {
            plugin.messages().send(sender, "scheduler.disabled");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "now", "ya" -> {
                plugin.schedules().runNow();
                plugin.messages().send(sender, "scheduler.forced");
            }
            case "skip", "saltar" -> {
                plugin.schedules().skip();
                plugin.messages().send(sender, "scheduler.skipped", Map.of(
                        "time", Durations.words(plugin.schedules().secondsUntilNext())));
            }
            default -> plugin.messages().send(sender, "commands.usage-schedule");
        }
    }


    private void create(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.create")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "commands.usage-create");
            return;
        }
        Arena arena = plugin.arenas().create(args[1]);
        if (arena == null) {
            plugin.messages().send(player, "arena.already-exists", Map.of("arena", args[1]));
            return;
        }
        plugin.selection().editing(player.getUniqueId(), arena.id());
        plugin.messages().send(player, "arena.created", Map.of("arena", arena.id()));

        // La región no es un paso aparte: si ya están las dos esquinas se crea aquí, y si
        // no, se creará en cuanto se marquen, sin más comandos.
        if (plugin.selection().bounds(player.getUniqueId()) != null) {
            applyBounds(player, arena, false);
        } else {
            plugin.messages().sendLines(player, "arena.mark-corners", Map.of("arena", arena.id()));
        }
    }

    private void select(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.edit")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "commands.usage-select");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(player, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        plugin.selection().editing(player.getUniqueId(), arena.id());
        plugin.messages().send(player, "arena.selected", Map.of("arena", arena.id()));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!allowed(sender, "siriusffa.delete")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage-delete");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        for (UUID uuid : plugin.sessions().playersIn(arena.id())) {
            Player inside = Bukkit.getPlayer(uuid);
            if (inside != null) {
                plugin.sessions().leave(inside, LeaveReason.ADMIN);
            }
        }
        plugin.worldGuard().remove(arena);
        plugin.arenas().delete(arena.id());
        plugin.messages().send(sender, "arena.deleted", Map.of("arena", arena.id()));
    }

    private void position(CommandSender sender, boolean first) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.edit")) {
            return;
        }
        Location location = player.getLocation();
        if (first) {
            plugin.selection().first(player.getUniqueId(), location);
        } else {
            plugin.selection().second(player.getUniqueId(), location);
        }
        plugin.messages().send(player, first ? "arena.pos1" : "arena.pos2", Map.of(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ())));

        autoBounds(player);
    }

    private void autoBounds(Player player) {
        String editing = plugin.selection().editing(player.getUniqueId());
        if (editing == null || plugin.selection().bounds(player.getUniqueId()) == null) {
            return;
        }
        Arena arena = plugin.arenas().get(editing);
        if (arena != null) {
            applyBounds(player, arena, false);
        }
    }

    private void bounds(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.edit")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "commands.usage-bounds");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(player, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        applyBounds(player, arena, false);
    }

    private void applyBounds(Player player, Arena arena, boolean silent) {
        Bounds bounds = plugin.selection().bounds(player.getUniqueId());
        if (bounds == null) {
            if (!silent) {
                plugin.messages().send(player, "arena.no-selection");
            }
            return;
        }
        arena.bounds(bounds);

        String region = plugin.worldGuard().apply(arena);
        arena.region(region);
        plugin.arenas().save(arena);

        plugin.messages().send(player, "arena.bounds-set", Map.of(
                "arena", arena.id(),
                "bounds", bounds.toString()));

        if (!region.isBlank()) {
            plugin.messages().sendLines(player, "arena.region-created", Map.of(
                    "arena", arena.id(),
                    "region", region));
        } else if (!plugin.worldGuard().available()) {
            plugin.messages().send(player, "arena.no-worldguard");
        } else {
            plugin.messages().send(player, "arena.region-failed", Map.of("arena", arena.id()));
        }
    }

    private void setSpawn(CommandSender sender, String[] args, boolean replace) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.edit")) {
            return;
        }
        Arena arena = arenaOrHere(player, args);
        if (arena == null) {
            return;
        }
        if (replace) {
            arena.setSpawn(player.getLocation());
        } else {
            arena.addSpawn(player.getLocation());
        }
        plugin.arenas().save(arena);
        plugin.messages().send(player, replace ? "arena.spawn-set" : "arena.spawn-added", Map.of(
                "arena", arena.id(),
                "spawns", String.valueOf(arena.spawns().size())));
    }

    private void clearSpawns(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.edit")) {
            return;
        }
        Arena arena = arenaOrHere(player, args);
        if (arena == null) {
            return;
        }
        arena.clearSpawns();
        plugin.arenas().save(arena);
        plugin.messages().send(player, "arena.spawns-cleared", Map.of("arena", arena.id()));
    }

    private Arena arenaOrHere(Player player, String[] args) {
        if (args.length > 1) {
            Arena arena = plugin.arenas().get(args[1]);
            if (arena == null) {
                plugin.messages().send(player, "arena.not-found", Map.of("arena", args[1]));
            }
            return arena;
        }
        for (Arena arena : plugin.arenas().all()) {
            if (arena.bounds() != null && arena.bounds().contains(player.getLocation())) {
                return arena;
            }
        }
        plugin.messages().send(player, "arena.not-here");
        return null;
    }

    private void setLobby(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.edit")) {
            return;
        }
        if (args.length < 2) {
            plugin.arenas().lobby(player.getLocation());
            plugin.messages().sendLines(player, "arena.lobby-set");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(player, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        if (arena.bounds() != null && arena.bounds().contains(player.getLocation())) {
            plugin.messages().send(player, "arena.lobby-inside", Map.of("arena", arena.id()));
            return;
        }
        arena.lobby(player.getLocation());
        plugin.arenas().save(arena);
        plugin.messages().send(player, "arena.lobby-set-arena", Map.of("arena", arena.id()));
    }

    private void setKit(CommandSender sender, String[] args) {
        if (!allowed(sender, "siriusffa.edit")) {
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "commands.usage-setkit");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        String kit = args[2].toLowerCase(Locale.ROOT);
        if (!kit.equals("none") && !plugin.kits().exists(kit)) {
            plugin.messages().send(sender, "kit.not-found", Map.of("kit", kit));
            return;
        }
        arena.kit(kit.equals("none") ? "" : kit);
        plugin.arenas().save(arena);
        plugin.messages().send(sender, "arena.kit-set", Map.of(
                "arena", arena.id(),
                "kit", arena.kitEnabled() ? arena.kit() : "-"));
    }

    private void privacy(CommandSender sender, String[] args) {
        if (!allowed(sender, "siriusffa.edit")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage-privacy");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            plugin.messages().send(sender, "arena.not-found", Map.of("arena", args[1]));
            return;
        }
        if (args.length < 3) {
            plugin.messages().sendRich(sender, "arena.privacy-current", Map.of(
                    "arena", arena.id(),
                    "access", plugin.messages().get(access(arena))));
            return;
        }
        ArenaAccess value = ArenaAccess.parse(args[2], null);
        if (value == null) {
            plugin.messages().send(sender, "commands.usage-privacy");
            return;
        }
        arena.access(value);
        plugin.arenas().save(arena);

        plugin.messages().sendRichLines(sender, value.open() ? "arena.privacy-open" : "arena.privacy-closed",
                Map.of("arena", arena.id(),
                        "players", String.valueOf(plugin.sessions().countIn(arena.id()))));
    }

    private String access(Arena arena) {
        return arena.reserved() ? "access.closed" : "access.open";
    }


    private void kitCreate(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.kit")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "commands.usage-kitcreate");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        plugin.kits().startEditing(player.getUniqueId(), id);
        plugin.messages().sendLines(player, "kit.editing", Map.of("kit", id));
    }

    private void kitSave(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || !allowed(player, "siriusffa.kit")) {
            return;
        }
        String id = args.length > 1
                ? args[1].toLowerCase(Locale.ROOT)
                : plugin.kits().editingOf(player.getUniqueId());

        if (id == null || id.isBlank()) {
            plugin.messages().send(player, "commands.usage-kitsave");
            return;
        }
        if (plugin.inventories().isEmpty(player)) {
            plugin.messages().send(player, "kit.empty-inventory");
            return;
        }
        Kit kit = plugin.kits().save(id, player);
        plugin.messages().send(player, "kit.saved", Map.of(
                "kit", id,
                "items", String.valueOf(kit.itemCount())));
    }

    private void kitDelete(CommandSender sender, String[] args) {
        if (!allowed(sender, "siriusffa.kit")) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "commands.usage-kitdelete");
            return;
        }
        if (!plugin.kits().delete(args[1])) {
            plugin.messages().send(sender, "kit.not-found", Map.of("kit", args[1]));
            return;
        }
        plugin.messages().send(sender, "kit.deleted", Map.of("kit", args[1].toLowerCase(Locale.ROOT)));
    }

    private void kitList(CommandSender sender) {
        if (!allowed(sender, "siriusffa.kit")) {
            return;
        }
        plugin.messages().send(sender, "kit.list", Map.of(
                "kits", plugin.kits().ids().isEmpty() ? "-" : String.join(", ", plugin.kits().ids()),
                "total", String.valueOf(plugin.kits().count())));
    }

    private void reload(CommandSender sender) {
        if (!allowed(sender, "siriusffa.reload")) {
            return;
        }
        plugin.reloadAll();
        plugin.messages().send(sender, "general.reloaded");
    }

    private void help(CommandSender sender) {
        plugin.messages().sendLines(sender, "help.player");
        if (sender.hasPermission("siriusffa.admin")) {
            plugin.messages().sendLines(sender, "help.admin");
        }
    }


    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "general.players-only");
        return null;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("siriusffa.admin")) {
            return true;
        }
        plugin.messages().send(sender, "general.no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(PLAYER_SUBS);
            if (sender.hasPermission("siriusffa.admin")) {
                options.addAll(ADMIN_SUBS);
            }
            return filter(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "join", "entrar" -> filter(withEvent(sender.hasPermission("siriusffa.admin")
                        ? plugin.arenas().ids()
                        : plugin.arenas().joinableIds()), args[1]);
                case "info", "delete", "borrar", "bounds", "limites", "select", "seleccionar",
                     "setspawn", "addspawn", "clearspawns", "setkit", "setlobby",
                     "privacy", "privacity", "privacidad" -> filter(plugin.arenas().ids(), args[1]);
                case "start", "iniciar" -> filter(plugin.events().ids(), args[1]);
                case "kitdelete", "kitsave" -> filter(new ArrayList<>(plugin.kits().ids()), args[1]);
                case "stats", "estadisticas" -> null;
                case "top" -> filter(Stat.PROFILE, args[1]);
                case "schedule", "programador" -> filter(List.of("now", "skip"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3
                && (sub.equals("privacy") || sub.equals("privacity") || sub.equals("privacidad"))) {
            return filter(List.of("open", "closed"), args[2]);
        }
        if (args.length == 3 && sub.equals("setkit")) {
            List<String> kits = new ArrayList<>(plugin.kits().ids());
            kits.add("none");
            return filter(kits, args[2]);
        }
        return List.of();
    }

    private List<String> withEvent(List<String> arenas) {
        List<String> options = new ArrayList<>(arenas);
        options.add("event");
        return options;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
