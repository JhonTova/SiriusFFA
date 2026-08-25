package me.sirius.ffa.event;

import me.sirius.ffa.command.CommandPolicy;
import me.sirius.ffa.kit.KitEffects;
import me.sirius.ffa.reward.KillReward;
import me.sirius.ffa.util.Durations;
import me.sirius.ffa.util.Players;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EventDefinition {

    private final String id;
    private final String mode;
    private final String arena;
    private final boolean enabled;
    private final String displayName;

    private final int minPlayers;
    private final int maxPlayers;
    private final int countdown;
    private final int endDelay;
    private final int maxTime;

    private final boolean allowLeave;
    private final boolean spectators;
    private final boolean requireEmptyInventory;

    private final Map<String, String> kits;
    private final Map<String, Role> roles;
    private final CommandPolicy commands;
    private final KillReward kill;

    private final List<String> winnerCommands;
    private final List<String> participationCommands;

    private final ConfigurationSection settings;

    private EventDefinition(String id, ConfigurationSection section) {
        this.id = id;
        this.mode = section.getString("mode", id).toLowerCase(Locale.ROOT);
        this.arena = section.getString("arena", "").toLowerCase(Locale.ROOT);
        this.enabled = section.getBoolean("enabled", true);
        this.displayName = section.getString("display-name", id);

        this.minPlayers = Math.max(2, section.getInt("min-players", 2));
        this.maxPlayers = section.getInt("max-players", 0);
        this.countdown = Math.max(0, section.getInt("countdown", 30));
        this.endDelay = Math.max(1, section.getInt("end-delay", 10));
        this.maxTime = Durations.seconds(section.getString("max-time", ""), 0);

        this.allowLeave = section.getBoolean("allow-leave", true);
        this.spectators = section.getBoolean("spectators", true);
        this.requireEmptyInventory = section.getBoolean("require-empty-inventory", true);

        Map<String, String> kits = new HashMap<>();
        ConfigurationSection kitSection = section.getConfigurationSection("kits");
        if (kitSection != null) {
            for (String key : kitSection.getKeys(false)) {
                kits.put(key.toLowerCase(Locale.ROOT),
                        kitSection.getString(key, "").toLowerCase(Locale.ROOT));
            }
        }
        this.kits = Map.copyOf(kits);

        this.roles = readRoles(section);
        this.commands = CommandPolicy.read(section.getConfigurationSection("commands"));
        this.kill = KillReward.read(section.getConfigurationSection("kill"));

        this.winnerCommands = List.copyOf(section.getStringList("rewards.winner.commands"));
        this.participationCommands = List.copyOf(section.getStringList("rewards.participation.commands"));

        this.settings = section;
    }

    private static Map<String, Role> readRoles(ConfigurationSection section) {
        Map<String, List<PotionEffect>> effects = new HashMap<>();
        Map<String, Integer> boosts = new HashMap<>();
        Set<String> glow = new HashSet<>();
        Set<String> keepFood = new HashSet<>();

        ConfigurationSection list = section.getConfigurationSection("effects");
        if (list != null) {
            for (String role : list.getKeys(false)) {
                List<PotionEffect> parsed = new ArrayList<>();
                for (String raw : list.getStringList(role)) {
                    PotionEffect effect = KitEffects.parse(raw);
                    if (effect != null) {
                        parsed.add(effect);
                    }
                }
                effects.put(role.toLowerCase(Locale.ROOT), List.copyOf(parsed));
            }
        }

        ConfigurationSection health = section.getConfigurationSection("health-boost");
        if (health != null) {
            for (String role : health.getKeys(false)) {
                boosts.put(role.toLowerCase(Locale.ROOT), health.getInt(role, 0));
            }
        }

        ConfigurationSection glowing = section.getConfigurationSection("glow");
        if (glowing != null) {
            for (String role : glowing.getKeys(false)) {
                if (glowing.getBoolean(role, false)) {
                    glow.add(role.toLowerCase(Locale.ROOT));
                }
            }
        }

        ConfigurationSection food = section.getConfigurationSection("keep-food");
        if (food != null) {
            for (String role : food.getKeys(false)) {
                if (food.getBoolean(role, false)) {
                    keepFood.add(role.toLowerCase(Locale.ROOT));
                }
            }
        }

        Map<String, Role> roles = new HashMap<>();
        Set<String> names = new HashSet<>(effects.keySet());
        names.addAll(boosts.keySet());
        names.addAll(glow);
        names.addAll(keepFood);
        for (String name : names) {
            roles.put(name, new Role(
                    effects.getOrDefault(name, List.of()),
                    boosts.getOrDefault(name, 0),
                    glow.contains(name),
                    keepFood.contains(name)));
        }
        return Map.copyOf(roles);
    }

    public static EventDefinition read(String id, ConfigurationSection section) {
        return new EventDefinition(id.toLowerCase(Locale.ROOT), section);
    }

    public String kit(String role) {
        return kits.getOrDefault(role.toLowerCase(Locale.ROOT), "");
    }

    public boolean usesKits() {
        return !kits.isEmpty();
    }

    public Map<String, String> kits() {
        return kits;
    }

    public Role role(String role) {
        return roles.getOrDefault(role == null ? "" : role.toLowerCase(Locale.ROOT), Role.NONE);
    }

    public record Role(List<PotionEffect> effects, int healthBoost, boolean glow, boolean keepFood) {

        public static final Role NONE = new Role(List.of(), 0, false, false);

        public boolean isEmpty() {
            return effects.isEmpty() && healthBoost <= 0 && !glow && !keepFood;
        }

        public void apply(Player player) {
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
            if (healthBoost > 0) {
                PotionEffect extra = KitEffects.parse("health_boost;0;" + (healthBoost - 1));
                if (extra != null) {
                    player.addPotionEffect(extra);
                }
            }
            if (glow) {
                PotionEffect glowing = KitEffects.parse("glowing;0;0");
                if (glowing != null) {
                    player.addPotionEffect(glowing);
                }
            }
            if (keepFood) {
                // Con la barra a tope y la saturación llena no empieza a bajar sola, y sin
                // eso el juggernaut deja de poder correr a mitad de su ronda.
                player.setFoodLevel(20);
                player.setSaturation(20F);
                player.setExhaustion(0F);
            }
            // La vida se llena después de los efectos: si el rol sube el máximo, la barra
            // que hay que llenar es la ya crecida.
            Players.heal(player);
        }
    }

    public String id() {
        return id;
    }

    public String mode() {
        return mode;
    }

    public String arena() {
        return arena;
    }

    public boolean enabled() {
        return enabled;
    }

    public String displayName() {
        return displayName;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int countdown() {
        return countdown;
    }

    public int endDelay() {
        return endDelay;
    }

    public int maxTime() {
        return maxTime;
    }

    public boolean allowLeave() {
        return allowLeave;
    }

    public boolean spectators() {
        return spectators;
    }

    public boolean requireEmptyInventory() {
        return requireEmptyInventory;
    }

    public CommandPolicy commands() {
        return commands;
    }

    public KillReward kill() {
        return kill;
    }

    public List<String> winnerCommands() {
        return winnerCommands;
    }

    public List<String> participationCommands() {
        return participationCommands;
    }

    public ConfigurationSection settings() {
        return settings;
    }

    public int setting(String path, int fallback) {
        return settings.getInt(path, fallback);
    }

    public boolean setting(String path, boolean fallback) {
        return settings.getBoolean(path, fallback);
    }

    public String setting(String path, String fallback) {
        return settings.getString(path, fallback);
    }

    public int seconds(String path, int fallback) {
        return Durations.seconds(settings.getString(path, ""), fallback);
    }
}
