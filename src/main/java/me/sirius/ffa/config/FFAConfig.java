package me.sirius.ffa.config;

import me.sirius.core.SiriusPlugin;
import me.sirius.core.config.Config;
import me.sirius.ffa.command.CommandPolicy;
import me.sirius.ffa.hud.EventBar;
import me.sirius.ffa.reward.KillReward;
import me.sirius.ffa.util.Durations;
import me.sirius.ffa.util.Sounds;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FFAConfig {

    public enum BoundaryAction {
        TELEPORT,
        PUSH_BACK,
        CANCEL
    }

    public enum ReturnMode {
        SAVED,
        LOBBY
    }

    public enum HudType { NONE, ACTIONBAR, SCOREBOARD, BOTH }

    private final SiriusPlugin plugin;
    private final Config file;

    private boolean instantRespawn;
    private int respawnDelay;
    private int defaultSpawnProtection;
    private int recoveryDelay;
    private boolean blockDrops;
    private boolean blockContainers;
    private boolean blockKitTransfer;
    private boolean hideDeathMessages;
    private boolean checkCombatOnJoin;

    private List<String> exitCommands;
    private int exitWindow;
    private ReturnMode returnMode;
    private boolean allowLeaveArena;

    private boolean preventExit;
    private BoundaryAction boundaryAction;

    private Set<TeleportCause> allowedCauses;

    private CommandPolicy global;
    private CommandPolicy kitArenas;
    private CommandPolicy noKitArenas;
    private CommandPolicy events;

    private HudType hudType;
    private boolean bossBarEnabled;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;

    private boolean removeFromArenaOnQuit;
    private boolean removeFromEventOnQuit;

    private boolean spectatorEnabled;

    private boolean invisible;
    private boolean invisibleEvents;
    private boolean invisibleArenas;
    private int invisibleSeconds;

    private boolean closeArenasDuringEvents;
    private boolean ejectArenasOnEventStart;
    private boolean pvpOffOnEnd;

    private boolean createRegions;
    private String regionPrefix;
    private boolean setPvpFlag;

    private boolean deluxeCombatEnabled;
    private String combatPlaceholder;
    private Set<String> combatTrueValues;

    private boolean statsEnabled;
    private int statsFlushSeconds;

    private boolean schedulerEnabled;
    private int schedulerInterval;
    private int schedulerFirstDelay;
    private int schedulerMinOnline;
    private Set<Integer> schedulerHours;
    private boolean schedulerRandom;
    private List<String> schedulerRotation;

    private KillReward defaultKill;
    private Map<String, Sounds.Cue> cues;

    public FFAConfig(SiriusPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.settings();
        read();
    }

    public void reload() {
        file.reload();
        read();
    }

    private void read() {
        this.instantRespawn = file.bool("general.instant-respawn", true);
        this.respawnDelay = file.integer("general.respawn-delay-ticks", 1);
        this.defaultSpawnProtection = file.integer("general.spawn-protection", 3);
        this.recoveryDelay = Math.max(1, file.integer("general.recovery-delay-ticks", 1));
        this.blockDrops = file.bool("general.block-item-drop", true);
        this.blockContainers = file.bool("general.block-containers", true);
        this.blockKitTransfer = file.bool("general.block-kit-transfer", true);
        this.hideDeathMessages = file.bool("general.hide-vanilla-death-messages", true);
        this.checkCombatOnJoin = file.bool("general.deny-join-in-combat", true);

        this.exitCommands = file.strings("leave.exit-commands").stream()
                .map(CommandPolicy::normalize).toList();
        this.exitWindow = file.integer("leave.exit-window-seconds", 5);
        this.returnMode = enumOf(ReturnMode.class, file.string("leave.return", "SAVED"), ReturnMode.SAVED);
        this.allowLeaveArena = file.bool("leave.allow-leave-arena", true);

        this.preventExit = file.bool("boundary.prevent-exit", true);
        this.boundaryAction = enumOf(BoundaryAction.class,
                file.string("boundary.action", "TELEPORT"), BoundaryAction.TELEPORT);

        Set<TeleportCause> causes = EnumSet.noneOf(TeleportCause.class);
        for (String raw : file.strings("teleport.allowed-causes")) {
            TeleportCause cause = enumOf(TeleportCause.class, raw, null);
            if (cause != null) {
                causes.add(cause);
            }
        }
        this.allowedCauses = causes;

        this.global = orAllow(CommandPolicy.read(file.section("commands.global")));
        this.kitArenas = CommandPolicy.read(file.section("commands.kit-arenas"));
        this.noKitArenas = CommandPolicy.read(file.section("commands.no-kit-arenas"));
        this.events = CommandPolicy.read(file.section("commands.events"));

        this.hudType = file.bool("hud.enabled", true)
                ? enumOf(HudType.class, file.string("hud.type", "SCOREBOARD"), HudType.SCOREBOARD)
                : HudType.NONE;
        this.bossBarEnabled = file.bool("hud.bossbar.enabled", true);
        this.bossBarColor = EventBar.color(file.string("hud.bossbar.color", "RED"), BarColor.RED);
        this.bossBarStyle = EventBar.style(file.string("hud.bossbar.style", "SOLID"), BarStyle.SOLID);

        this.removeFromArenaOnQuit = file.bool("disconnect.remove-from-arena", true);
        this.removeFromEventOnQuit = file.bool("disconnect.remove-from-event", true);

        this.spectatorEnabled = file.bool("spectator.enabled", true);

        this.invisible = file.bool("invisible.enabled", false);
        this.invisibleEvents = file.bool("invisible.events", true);
        this.invisibleArenas = file.bool("invisible.arenas", true);
        // Nunca permanente y nunca eterno: el tope existe para que a quien se caiga del
        // servidor sin pasar por la salida se le acabe solo.
        this.invisibleSeconds = Math.max(60, Math.min(3600,
                Durations.seconds(file.string("invisible.duration", "15m"), 900)));

        this.closeArenasDuringEvents = file.bool("events.close-arenas", true);
        this.ejectArenasOnEventStart = file.bool("events.eject-arena-players", false);
        this.pvpOffOnEnd = file.bool("events.pvp-off-on-end", true);

        this.createRegions = file.bool("worldguard.create-regions", true);
        this.regionPrefix = file.string("worldguard.region-prefix", "siriusffa_");
        this.setPvpFlag = file.bool("worldguard.set-pvp-flag", true);

        this.deluxeCombatEnabled = file.bool("deluxecombat.enabled", true);
        this.combatPlaceholder = file.string("deluxecombat.combat-placeholder", "%deluxecombat_incombat%");

        Set<String> trueValues = new HashSet<>();
        for (String value : file.strings("deluxecombat.true-values")) {
            trueValues.add(value.toLowerCase(Locale.ROOT));
        }
        this.combatTrueValues = trueValues.isEmpty() ? Set.of("true", "yes", "1") : Set.copyOf(trueValues);

        this.statsEnabled = file.bool("stats.enabled", true);
        this.statsFlushSeconds = Math.max(30, file.integer("stats.flush-seconds", 300));

        this.schedulerEnabled = file.bool("scheduler.enabled", false);
        this.schedulerInterval = Math.max(60, Durations.seconds(file.string("scheduler.interval", "2h"), 7200));
        this.schedulerFirstDelay = Math.max(30, Durations.seconds(file.string("scheduler.first-delay", "30m"), 1800));
        this.schedulerMinOnline = Math.max(0, file.integer("scheduler.min-online", 6));
        this.schedulerRandom = file.bool("scheduler.random", false);
        this.schedulerRotation = List.copyOf(file.strings("scheduler.rotation"));

        Set<Integer> hours = new HashSet<>();
        for (String raw : file.raw().getStringList("scheduler.hours")) {
            try {
                hours.add(Integer.parseInt(raw.trim()));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("La hora '" + raw + "' de scheduler.hours no es un número.");
            }
        }
        for (int hour : file.raw().getIntegerList("scheduler.hours")) {
            hours.add(hour);
        }
        this.schedulerHours = Set.copyOf(hours);

        this.defaultKill = KillReward.read(file.section("kill"));

        Map<String, Sounds.Cue> cues = new HashMap<>();
        ConfigurationSection sounds = file.section("sounds");
        if (sounds != null && sounds.getBoolean("enabled", true)) {
            for (String name : sounds.getKeys(false)) {
                Sounds.Cue cue = Sounds.parse(sounds.getString(name, ""));
                if (cue != null) {
                    cues.put(name.toLowerCase(Locale.ROOT), cue);
                }
            }
        }
        this.cues = Map.copyOf(cues);
    }

    private static CommandPolicy orAllow(CommandPolicy policy) {
        return policy == null ? CommandPolicy.ALLOW_ALL : policy;
    }

    private static <T extends Enum<T>> T enumOf(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public Config file() {
        return file;
    }

    public boolean instantRespawn() {
        return instantRespawn;
    }

    public int respawnDelay() {
        return Math.max(1, respawnDelay);
    }

    public int defaultSpawnProtection() {
        return defaultSpawnProtection;
    }

    public int recoveryDelay() {
        return recoveryDelay;
    }

    public boolean blockDrops() {
        return blockDrops;
    }

    public boolean blockContainers() {
        return blockContainers;
    }

    public boolean blockKitTransfer() {
        return blockKitTransfer;
    }

    public boolean hideDeathMessages() {
        return hideDeathMessages;
    }

    public boolean checkCombatOnJoin() {
        return checkCombatOnJoin;
    }

    public List<String> exitCommands() {
        return exitCommands;
    }

    public int exitWindow() {
        return Math.max(1, exitWindow);
    }

    public ReturnMode returnMode() {
        return returnMode;
    }

    public boolean allowLeaveArena() {
        return allowLeaveArena;
    }

    public boolean preventExit() {
        return preventExit;
    }

    public BoundaryAction boundaryAction() {
        return boundaryAction;
    }

    public boolean allowsTeleport(TeleportCause cause) {
        return allowedCauses.contains(cause);
    }

    public CommandPolicy globalPolicy() {
        return global;
    }

    public CommandPolicy kitArenaPolicy() {
        return kitArenas;
    }

    public CommandPolicy noKitArenaPolicy() {
        return noKitArenas;
    }

    public CommandPolicy eventPolicy() {
        return events;
    }

    public HudType hudType() {
        return hudType;
    }

    public boolean bossBarEnabled() {
        return bossBarEnabled;
    }

    public BarColor bossBarColor() {
        return bossBarColor;
    }

    public BarStyle bossBarStyle() {
        return bossBarStyle;
    }

    public Sounds.Cue cue(String name) {
        return name == null ? null : cues.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean removeFromArenaOnQuit() {
        return removeFromArenaOnQuit;
    }

    public boolean removeFromEventOnQuit() {
        return removeFromEventOnQuit;
    }

    public boolean spectatorEnabled() {
        return spectatorEnabled;
    }

    public boolean invisible() {
        return invisible;
    }

    // Interruptor en caliente: se guarda en config.yml para que aguante un reinicio.
    public void invisible(boolean value) {
        this.invisible = value;
        file.set("invisible.enabled", value);
        file.save();
    }

    public boolean invisibleInEvents() {
        return invisibleEvents;
    }

    public boolean invisibleInArenas() {
        return invisibleArenas;
    }

    public int invisibleSeconds() {
        return invisibleSeconds;
    }

    public boolean closeArenasDuringEvents() {
        return closeArenasDuringEvents;
    }

    public boolean ejectArenasOnEventStart() {
        return ejectArenasOnEventStart;
    }

    public boolean pvpOffOnEnd() {
        return pvpOffOnEnd;
    }

    public boolean createRegions() {
        return createRegions;
    }

    public String regionPrefix() {
        return regionPrefix;
    }

    public boolean setPvpFlag() {
        return setPvpFlag;
    }

    public boolean deluxeCombatEnabled() {
        return deluxeCombatEnabled;
    }

    public String combatPlaceholder() {
        return combatPlaceholder;
    }

    public Set<String> combatTrueValues() {
        return combatTrueValues;
    }

    public boolean statsEnabled() {
        return statsEnabled;
    }

    public int statsFlushSeconds() {
        return statsFlushSeconds;
    }

    public KillReward defaultKill() {
        return defaultKill;
    }

    public boolean schedulerEnabled() {
        return schedulerEnabled;
    }

    public int schedulerInterval() {
        return schedulerInterval;
    }

    public int schedulerFirstDelay() {
        return schedulerFirstDelay;
    }

    public int schedulerMinOnline() {
        return schedulerMinOnline;
    }

    public Set<Integer> schedulerHours() {
        return schedulerHours;
    }

    public boolean schedulerRandom() {
        return schedulerRandom;
    }

    public List<String> schedulerRotation() {
        return schedulerRotation;
    }
}
