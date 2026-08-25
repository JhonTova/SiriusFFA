package me.sirius.ffa.hud;

import me.sirius.core.text.Text;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.config.Messages;
import me.sirius.ffa.event.EventState;
import me.sirius.ffa.event.FFAEvent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventBar {

    private final SiriusFFA plugin;

    private BossBar bar;

    public EventBar(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    public void update(FFAEvent event) {
        if (event == null || !plugin.config().bossBarEnabled()) {
            clear();
            return;
        }
        String key = key(event);
        String line = plugin.messages().get("bar." + key);
        if (line.isBlank() || line.equals("bar." + key)) {
            // Sin línea escrita para esta fase no se pinta nada: es la forma de apagar
            // la barra de un modo concreto sin tocar la de los demás.
            clear();
            return;
        }

        Map<String, String> data = data(event);
        BossBar live = bar();
        live.setTitle(Text.legacy(Messages.fill(line, data)));
        live.setProgress(Math.clamp(progress(event), 0D, 1D));
        live.setColor(color(event));
        sync(event.players());
    }

    public void remove(Player player) {
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    public void clear() {
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
    }

    private BossBar bar() {
        if (bar == null) {
            bar = Bukkit.createBossBar("", plugin.config().bossBarColor(), plugin.config().bossBarStyle());
        }
        return bar;
    }

    private String key(FFAEvent event) {
        return switch (event.state()) {
            case WAITING -> "waiting";
            case STARTING -> "starting";
            case ENDING -> "ending";
            default -> event.mode().id();
        };
    }

    private Map<String, String> data(FFAEvent event) {
        Map<String, String> data = new HashMap<>(event.data(Map.of()));
        data.putAll(event.mode().barData());
        data.put("min", String.valueOf(event.definition().minPlayers()));
        data.put("seconds", String.valueOf(switch (event.state()) {
            case STARTING -> event.countdown();
            case ENDING -> event.endTimer();
            default -> 0;
        }));
        return data;
    }

    private double progress(FFAEvent event) {
        return switch (event.state()) {
            case WAITING -> event.definition().minPlayers() <= 0
                    ? 1D : (double) event.size() / event.definition().minPlayers();
            case STARTING -> event.definition().countdown() <= 0
                    ? 1D : (double) event.countdown() / event.definition().countdown();
            case ENDING -> event.definition().endDelay() <= 0
                    ? 1D : (double) event.endTimer() / event.definition().endDelay();
            default -> active(event);
        };
    }

    private double active(FFAEvent event) {
        double own = event.mode().barProgress();
        if (own >= 0D) {
            return own;
        }
        int max = event.definition().maxTime();
        return max <= 0 ? 1D : (double) Math.max(0, max - event.elapsed()) / max;
    }

    private BarColor color(FFAEvent event) {
        String own = event.state() == EventState.ACTIVE
                ? event.mode().barColor() : null;
        if (own == null || own.isBlank()) {
            return plugin.config().bossBarColor();
        }
        try {
            return BarColor.valueOf(own.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return plugin.config().bossBarColor();
        }
    }

    private void sync(List<Player> inside) {
        BossBar live = bar();
        for (Player watching : List.copyOf(live.getPlayers())) {
            if (!inside.contains(watching)) {
                live.removePlayer(watching);
            }
        }
        for (Player player : inside) {
            if (!live.getPlayers().contains(player)) {
                live.addPlayer(player);
            }
        }
    }

    public static BarStyle style(String name, BarStyle fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return BarStyle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static BarColor color(String name, BarColor fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return BarColor.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
