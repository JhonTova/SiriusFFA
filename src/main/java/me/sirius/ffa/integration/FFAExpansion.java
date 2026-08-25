package me.sirius.ffa.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.FFASession;
import me.sirius.ffa.stats.PlayerStats;
import me.sirius.ffa.stats.Stat;
import me.sirius.ffa.stats.StatsService;
import me.sirius.ffa.util.Durations;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

public final class FFAExpansion extends PlaceholderExpansion {

    private final SiriusFFA plugin;

    public FFAExpansion(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "siriusffa";
    }

    @Override
    public String getAuthor() {
        return "Sirius";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.startsWith("top_")) {
            return top(key.substring("top_".length()));
        }
        if (key.startsWith("arena_players_")) {
            return String.valueOf(plugin.sessions().countIn(key.substring("arena_players_".length())));
        }
        if (key.startsWith("event")) {
            String live = event(key);
            if (live != null) {
                return live;
            }
        }
        if (key.equals("next_event")) {
            return plugin.schedules().nextEvent();
        }
        if (key.equals("next_time")) {
            int seconds = plugin.schedules().secondsUntilNext();
            return seconds < 0 ? "-" : Durations.clock(seconds);
        }
        if (player == null) {
            return "";
        }

        String session = session(player, key);
        return session != null ? session : stats(player, key);
    }

    private String event(String key) {
        FFAEvent live = plugin.events().active();
        return switch (key) {
            case "event" -> live == null ? "" : live.definition().displayName();
            case "event_mode" -> live == null ? "" : live.mode().id();
            case "event_state" -> live == null
                    ? plugin.messages().get("state.idle")
                    : plugin.messages().get("state." + live.state().name().toLowerCase(Locale.ROOT));
            case "event_players" -> String.valueOf(live == null ? 0 : live.size());
            case "event_round" -> String.valueOf(live == null ? 0 : live.round());
            case "event_time" -> Durations.clock(live == null ? 0 : live.elapsed());
            case "event_countdown" -> Durations.clock(live == null ? 0 : live.countdown());
            default -> null;
        };
    }

    private String session(OfflinePlayer player, String key) {
        FFASession session = plugin.sessions().of(player.getUniqueId());
        return switch (key) {
            case "in_ffa" -> String.valueOf(session != null);
            case "in_arena" -> String.valueOf(session != null && !session.inEvent());
            case "in_event" -> String.valueOf(session != null && session.inEvent());
            case "arena" -> {
                if (session == null) {
                    yield "";
                }
                var arena = plugin.arenas().get(session.arena());
                yield arena == null ? session.arena() : arena.displayName();
            }
            case "arena_id" -> session == null ? "" : session.arena();
            case "kills" -> String.valueOf(session == null ? 0 : session.kills());
            case "deaths" -> String.valueOf(session == null ? 0 : session.deaths());
            case "streak" -> String.valueOf(session == null ? 0 : session.streak());
            default -> null;
        };
    }

    private String stats(OfflinePlayer player, String key) {
        PlayerStats stats = plugin.stats().cached(player.getUniqueId());
        if (stats == null) {
            return plugin.messages().get("placeholders.loading");
        }
        if (key.startsWith("stat_")) {
            return String.valueOf(stats.get(key.substring("stat_".length())));
        }
        return switch (key) {
            case "total_kills" -> String.valueOf(stats.get(Stat.KILLS));
            case "total_deaths" -> String.valueOf(stats.get(Stat.DEATHS));
            case "ratio" -> String.format(Locale.ROOT, "%.2f", stats.ratio());
            case "best_streak" -> String.valueOf(stats.get(Stat.BEST_STREAK));
            case "events" -> String.valueOf(stats.get(Stat.EVENTS_PLAYED));
            case "wins" -> String.valueOf(stats.get(Stat.EVENT_WINS));
            case "juggernaut_wins" -> String.valueOf(stats.get(Stat.JUGGERNAUT_WINS));
            case "playtime" -> Durations.words((int) stats.get(Stat.PLAYTIME));
            default -> null;
        };
    }

    private String top(String params) {
        boolean wantsName = params.startsWith("name_");
        boolean wantsValue = params.startsWith("value_");
        if (!wantsName && !wantsValue) {
            return "";
        }
        String rest = params.substring(wantsName ? "name_".length() : "value_".length());

        int split = rest.lastIndexOf('_');
        if (split <= 0) {
            return "";
        }
        String stat = rest.substring(0, split);

        int position;
        try {
            position = Integer.parseInt(rest.substring(split + 1));
        } catch (NumberFormatException e) {
            return "";
        }

        List<StatsService.Ranked> ranking = plugin.stats().cachedTop(stat);
        if (position < 1 || position > ranking.size()) {
            return wantsName ? plugin.messages().get("placeholders.empty") : "0";
        }
        StatsService.Ranked entry = ranking.get(position - 1);
        return wantsName ? entry.name() : String.valueOf(entry.value());
    }
}
