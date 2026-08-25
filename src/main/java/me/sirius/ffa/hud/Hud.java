package me.sirius.ffa.hud;

import me.sirius.core.text.Text;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.config.FFAConfig;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Hud {

    private static final String OBJECTIVE = "siriusffa";
    private static final int MAX_LINES = 14;

    private final SiriusFFA plugin;
    private final EventBar bar;

    public Hud(SiriusFFA plugin) {
        this.plugin = plugin;
        this.bar = new EventBar(plugin);
    }

    public EventBar bar() {
        return bar;
    }

    public void update(Player player, FFASession session) {
        FFAConfig.HudType type = plugin.config().hudType();
        if (type == FFAConfig.HudType.NONE) {
            return;
        }
        Map<String, String> data = data(player, session);
        String key = key(session);

        if (type == FFAConfig.HudType.ACTIONBAR || type == FFAConfig.HudType.BOTH) {
            plugin.messages().actionBar(player, "hud." + key + "-actionbar", data);
        }
        if (type == FFAConfig.HudType.SCOREBOARD || type == FFAConfig.HudType.BOTH) {
            scoreboard(player, session, key, data);
        }
    }

    private String key(FFASession session) {
        if (!session.inEvent()) {
            return "arena";
        }
        FFAEvent event = plugin.events().active();
        return event == null ? "arena" : event.mode().id();
    }

    private Map<String, String> data(Player player, FFASession session) {
        Map<String, String> data = new HashMap<>();
        data.put("player", player.getName());
        data.put("kills", String.valueOf(session.kills()));
        data.put("deaths", String.valueOf(session.deaths()));
        data.put("streak", String.valueOf(session.streak()));
        data.put("arena", arenaName(session));

        FFAEvent event = session.inEvent() ? plugin.events().active() : null;
        if (event == null) {
            data.put("players", String.valueOf(plugin.sessions().countIn(session.arena())));
            return data;
        }
        data.putAll(event.data(Map.of()));
        data.putAll(event.mode().hudData(player));
        data.put("state", plugin.messages().get("state." + event.state().name().toLowerCase()));
        return data;
    }

    private String arenaName(FFASession session) {
        var arena = plugin.arenas().get(session.arena());
        return arena == null ? session.arena() : arena.displayName();
    }

    private void scoreboard(Player player, FFASession session, String key, Map<String, String> data) {
        List<String> lines = plugin.messages().lines("hud." + key);
        if (lines.isEmpty()) {
            return;
        }

        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE);

        if (objective == null) {
            if (session.previousScoreboard() == null) {
                session.previousScoreboard(board);
            }
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Text.legacy(plugin.messages().get("hud.title", data)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(board);
        } else {
            objective.setDisplayName(Text.legacy(plugin.messages().get("hud.title", data)));
        }

        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            String text = Text.legacy(me.sirius.ffa.config.Messages.fill(lines.get(i), data));
            entry(board, objective, i, text, count - i);
        }
        // Sobran líneas de un modo anterior: se quitan en vez de dejarlas congeladas.
        for (int i = count; i < MAX_LINES; i++) {
            Team team = board.getTeam("ffa" + i);
            if (team != null) {
                board.resetScores(marker(i));
                team.unregister();
            }
        }
    }

    private void entry(Scoreboard board, Objective objective, int index, String text, int score) {
        String marker = marker(index);
        Team team = board.getTeam("ffa" + index);
        if (team == null) {
            team = board.registerNewTeam("ffa" + index);
            team.addEntry(marker);
            objective.getScore(marker).setScore(score);
        }
        if (!text.equals(team.getPrefix())) {
            team.setPrefix(text);
        }
    }

    private String marker(int index) {
        ChatColor[] colors = ChatColor.values();
        return colors[index % colors.length].toString() + ChatColor.RESET;
    }

    public void clear(Player player, FFASession session) {
        bar.remove(player);
        Scoreboard previous = session.previousScoreboard();
        if (previous != null) {
            player.setScoreboard(previous);
            session.previousScoreboard(null);
            return;
        }
        Scoreboard board = player.getScoreboard();
        if (board.getObjective(OBJECTIVE) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
