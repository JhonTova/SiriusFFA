package me.sirius.ffa.event;

import me.sirius.core.scheduler.Scheduler;
import me.sirius.ffa.SiriusFFA;
import org.bukkit.Bukkit;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class EventScheduler {

    private static final long CHECK_SECONDS = 20L;

    private static final int RETRY_SECONDS = 300;

    private final SiriusFFA plugin;

    private Scheduler.Task task;
    private long nextRun;
    private int index;

    public EventScheduler(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.config().schedulerEnabled()) {
            return;
        }
        this.nextRun = System.currentTimeMillis() + plugin.config().schedulerFirstDelay() * 1000L;
        this.task = plugin.scheduler().syncTimer(this::tick, CHECK_SECONDS * 20L, CHECK_SECONDS * 20L);

        plugin.getLogger().info("Eventos automáticos activados: el primero en "
                + me.sirius.ffa.util.Durations.words(plugin.config().schedulerFirstDelay()) + ".");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        start();
    }

    public boolean enabled() {
        return task != null;
    }

    public int secondsUntilNext() {
        if (!enabled()) {
            return -1;
        }
        return (int) Math.max(0, (nextRun - System.currentTimeMillis()) / 1000L);
    }

    public String nextEvent() {
        List<String> rotation = rotation();
        if (rotation.isEmpty()) {
            return "";
        }
        return plugin.config().schedulerRandom() ? "" : rotation.get(index % rotation.size());
    }

    public void runNow() {
        this.nextRun = System.currentTimeMillis();
    }

    public void skip() {
        this.nextRun = System.currentTimeMillis() + plugin.config().schedulerInterval() * 1000L;
        this.index++;
    }

    private void tick() {
        long now = System.currentTimeMillis();

        if (plugin.events().active() != null) {
            // Ya hay evento: la cuenta empieza cuando termine, no mientras se juega.
            nextRun = now + plugin.config().schedulerInterval() * 1000L;
            return;
        }
        if (now < nextRun) {
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < plugin.config().schedulerMinOnline() || !insideHours()) {
            return;
        }

        String id = pick();
        if (id.isEmpty()) {
            plugin.getLogger().warning("Los eventos automáticos están activados pero no hay "
                    + "ninguno que lanzar: revisa 'scheduler.rotation' y qué eventos tienes activados.");
            nextRun = now + RETRY_SECONDS * 1000L;
            return;
        }

        EventManager.StartResult result = plugin.events().start(id);
        if (result.ok()) {
            nextRun = now + plugin.config().schedulerInterval() * 1000L;
            plugin.getLogger().info("Evento automático lanzado: " + id);
            return;
        }

        plugin.getLogger().warning("El evento automático '" + id + "' no se pudo lanzar ("
                + result.name() + "). Se reintentará con el siguiente de la rotación.");
        index++;
        nextRun = now + RETRY_SECONDS * 1000L;
    }

    private boolean insideHours() {
        var hours = plugin.config().schedulerHours();
        return hours.isEmpty() || hours.contains(LocalTime.now().getHour());
    }

    private String pick() {
        List<String> rotation = rotation();
        if (rotation.isEmpty()) {
            return "";
        }
        if (plugin.config().schedulerRandom()) {
            return rotation.get(ThreadLocalRandom.current().nextInt(rotation.size()));
        }
        String id = rotation.get(index % rotation.size());
        index++;
        return id;
    }

    private List<String> rotation() {
        List<String> out = new ArrayList<>();
        for (String id : plugin.config().schedulerRotation()) {
            EventDefinition definition = plugin.events().definition(id);
            if (definition != null && definition.enabled()) {
                out.add(definition.id());
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        for (EventDefinition definition : plugin.events().definitions()) {
            if (definition.enabled()) {
                out.add(definition.id());
            }
        }
        return out;
    }
}
