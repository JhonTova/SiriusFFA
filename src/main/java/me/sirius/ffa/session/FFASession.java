package me.sirius.ffa.session;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

public final class FFASession {

    private final UUID player;
    private final String playerName;
    private final SessionKind kind;
    private final String arena;
    private final String event;
    private final boolean protectedInventory;
    private final long joinedAt;

    private int kills;
    private int deaths;
    private int streak;
    private int bestStreak;
    private boolean spectator;

    private long spawnProtectionUntil;

    private long exitingUntil;

    private boolean teleportAllowed;

    private Scoreboard previousScoreboard;

    FFASession(Player player, SessionKind kind, String arena, String event, boolean protectedInventory) {
        this.player = player.getUniqueId();
        this.playerName = player.getName();
        this.kind = kind;
        this.arena = arena;
        this.event = event;
        this.protectedInventory = protectedInventory;
        this.joinedAt = System.currentTimeMillis();
    }

    public UUID player() {
        return player;
    }

    public String playerName() {
        return playerName;
    }

    public SessionKind kind() {
        return kind;
    }

    public String arena() {
        return arena;
    }

    public String event() {
        return event;
    }

    public boolean inEvent() {
        return kind == SessionKind.EVENT;
    }

    public boolean protectedInventory() {
        return protectedInventory;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public int secondsPlayed() {
        return (int) ((System.currentTimeMillis() - joinedAt) / 1000L);
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int streak() {
        return streak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public void addKill() {
        kills++;
        streak++;
        if (streak > bestStreak) {
            bestStreak = streak;
        }
    }

    public void addDeath() {
        deaths++;
        streak = 0;
    }

    public boolean spectator() {
        return spectator;
    }

    public void spectator(boolean value) {
        this.spectator = value;
    }

    public void protectSpawn(int seconds) {
        this.spawnProtectionUntil = seconds <= 0 ? 0 : System.currentTimeMillis() + seconds * 1000L;
    }

    public boolean spawnProtected() {
        return spawnProtectionUntil > System.currentTimeMillis();
    }

    public void dropSpawnProtection() {
        this.spawnProtectionUntil = 0;
    }

    public void exiting(int seconds) {
        this.exitingUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public boolean exiting() {
        return exitingUntil > System.currentTimeMillis();
    }

    public void allowTeleport() {
        this.teleportAllowed = true;
    }

    public boolean consumeTeleport() {
        boolean allowed = teleportAllowed;
        teleportAllowed = false;
        return allowed;
    }

    public Scoreboard previousScoreboard() {
        return previousScoreboard;
    }

    public void previousScoreboard(Scoreboard scoreboard) {
        this.previousScoreboard = scoreboard;
    }
}
