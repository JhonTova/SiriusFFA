package me.sirius.ffa.stats;

import java.util.List;

public final class Stat {

    public static final String KILLS = "kills";
    public static final String DEATHS = "deaths";
    public static final String BEST_STREAK = "best_streak";
    public static final String PLAYTIME = "playtime";
    public static final String ARENAS_JOINED = "arenas_joined";

    public static final String EVENTS_PLAYED = "events_played";
    public static final String EVENT_WINS = "event_wins";
    public static final String EVENT_LOSSES = "event_losses";

    public static final String JUGGERNAUT_ROUNDS = "juggernaut_rounds";
    public static final String JUGGERNAUT_WINS = "juggernaut_wins";
    public static final String JUGGERNAUT_DEATHS = "juggernaut_deaths";
    public static final String JUGGERNAUT_KILLS = "juggernaut_kills";

    public static final String GUNGAME_KILLS = "gungame_kills";
    public static final String GUNGAME_WINS = "gungame_wins";
    public static final String OITC_KILLS = "oitc_kills";
    public static final String OITC_WINS = "oitc_wins";

    public static final List<String> PROFILE = List.of(
            KILLS, DEATHS, BEST_STREAK, EVENTS_PLAYED, EVENT_WINS, JUGGERNAUT_WINS, PLAYTIME);

    private Stat() {
    }

    public static double ratio(long kills, long deaths) {
        return deaths <= 0 ? kills : (double) kills / deaths;
    }
}
