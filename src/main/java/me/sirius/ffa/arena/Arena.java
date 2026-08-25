package me.sirius.ffa.arena;

import me.sirius.ffa.command.CommandPolicy;
import me.sirius.ffa.reward.KillReward;
import me.sirius.ffa.util.Locations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Arena {

    private final String id;
    private String displayName;
    private boolean enabled = true;

    private boolean kitEnabled;
    private String kit;
    private boolean requireEmptyInventory = true;

    private int maxPlayers;
    private Bounds bounds;
    private final List<Location> spawns = new ArrayList<>();

    private Location lobby;

    private CommandPolicy commands;
    private KillReward killReward = KillReward.NONE;

    private ArenaAccess access = ArenaAccess.OPEN;
    private int spawnProtection;
    private boolean keepDrops;
    private Material icon = Material.IRON_SWORD;
    private String region = "";

    public Arena(String id) {
        this.id = id.toLowerCase();
        this.displayName = this.id;
    }

    public static Arena read(String id, ConfigurationSection section) {
        Arena arena = new Arena(id);
        arena.displayName = section.getString("display-name", id);
        arena.enabled = section.getBoolean("enabled", true);
        arena.kitEnabled = section.getBoolean("kit-enabled", false);
        arena.kit = section.getString("kit", "");
        arena.requireEmptyInventory = section.getBoolean("require-empty-inventory", arena.kitEnabled);
        arena.maxPlayers = section.getInt("max-players", 0);
        arena.bounds = Bounds.decode(section.getString("bounds", ""));
        arena.access = readAccess(section);
        arena.spawnProtection = section.getInt("spawn-protection", 0);
        arena.keepDrops = section.getBoolean("keep-drops", false);
        arena.region = section.getString("region", "");
        arena.lobby = Locations.decode(section.getString("lobby", ""));

        for (String raw : section.getStringList("spawns")) {
            Location spawn = Locations.decode(raw);
            if (spawn != null) {
                arena.spawns.add(spawn);
            }
        }

        Material icon = Material.matchMaterial(section.getString("icon", "IRON_SWORD"));
        arena.icon = icon == null ? Material.IRON_SWORD : icon;

        arena.commands = CommandPolicy.read(section.getConfigurationSection("commands"));
        arena.killReward = KillReward.read(section.getConfigurationSection("kill"));
        return arena;
    }

    private static ArenaAccess readAccess(ConfigurationSection section) {
        ArenaAccess legacy = section.getBoolean("for-events", false)
                ? ArenaAccess.CLOSED
                : ArenaAccess.OPEN;
        return ArenaAccess.parse(section.getString("access", ""), legacy);
    }

    public void write(ConfigurationSection section) {
        section.set("display-name", displayName);
        section.set("enabled", enabled);
        section.set("kit-enabled", kitEnabled);
        section.set("kit", kit);
        section.set("require-empty-inventory", requireEmptyInventory);
        section.set("max-players", maxPlayers);
        section.set("bounds", bounds == null ? "" : bounds.encode());
        section.set("access", access.name());
        section.set("for-events", null);
        section.set("spawn-protection", spawnProtection);
        section.set("keep-drops", keepDrops);
        section.set("region", region);
        section.set("icon", icon.name());
        section.set("lobby", lobby == null ? "" : Locations.encode(lobby));

        List<String> encoded = new ArrayList<>(spawns.size());
        for (Location spawn : spawns) {
            encoded.add(Locations.encode(spawn));
        }
        section.set("spawns", encoded);
    }

    public Location spawn() {
        if (spawns.isEmpty()) {
            return null;
        }
        if (spawns.size() == 1) {
            return spawns.get(0).clone();
        }
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())).clone();
    }

    public Location spawnAwayFrom(Location from) {
        if (spawns.size() < 2 || from == null) {
            return spawn();
        }
        Location best = null;
        double bestDistance = -1;
        for (Location spawn : spawns) {
            if (spawn.getWorld() != from.getWorld()) {
                continue;
            }
            double distance = spawn.distanceSquared(from);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = spawn;
            }
        }
        return best == null ? spawn() : best.clone();
    }

    public Location lobby() {
        return lobby == null ? null : lobby.clone();
    }

    public void lobby(Location location) {
        this.lobby = location == null ? null : location.clone();
    }

    public boolean isReady() {
        return !spawns.isEmpty();
    }

    public boolean inside(Location location) {
        return bounds == null || bounds.contains(location);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String value) {
        this.displayName = value;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean value) {
        this.enabled = value;
    }

    public boolean kitEnabled() {
        return kitEnabled;
    }

    public void kitEnabled(boolean value) {
        this.kitEnabled = value;
    }

    public String kit() {
        return kit;
    }

    public void kit(String value) {
        this.kit = value == null ? "" : value.toLowerCase();
        this.kitEnabled = !this.kit.isBlank();
    }

    public boolean requireEmptyInventory() {
        return requireEmptyInventory;
    }

    public void requireEmptyInventory(boolean value) {
        this.requireEmptyInventory = value;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public void maxPlayers(int value) {
        this.maxPlayers = Math.max(0, value);
    }

    public Bounds bounds() {
        return bounds;
    }

    public void bounds(Bounds value) {
        this.bounds = value;
    }

    public List<Location> spawns() {
        return List.copyOf(spawns);
    }

    public void addSpawn(Location location) {
        spawns.add(location.clone());
    }

    public void setSpawn(Location location) {
        spawns.clear();
        spawns.add(location.clone());
    }

    public void clearSpawns() {
        spawns.clear();
    }

    public CommandPolicy commands() {
        return commands;
    }

    public KillReward killReward() {
        return killReward;
    }

    public ArenaAccess access() {
        return access;
    }

    public void access(ArenaAccess value) {
        this.access = value == null ? ArenaAccess.OPEN : value;
    }

    public boolean reserved() {
        return access.closed();
    }

    public int spawnProtection() {
        return spawnProtection;
    }

    public boolean keepDrops() {
        return keepDrops;
    }

    public Material icon() {
        return icon;
    }

    public String region() {
        return region;
    }

    public void region(String value) {
        this.region = value == null ? "" : value;
    }
}
