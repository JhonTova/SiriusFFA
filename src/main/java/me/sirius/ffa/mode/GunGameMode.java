package me.sirius.ffa.mode;

import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.kit.Kit;
import me.sirius.ffa.stats.Stat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class GunGameMode extends FFAMode {

    private static final String LEVEL = "gungame_level";

    private final TreeMap<Integer, String> levels = new TreeMap<>();

    private final Map<Integer, VanillaLadder.Rung> ladder = new HashMap<>();

    private final boolean loseLevelOnDeath;

    public GunGameMode(FFAEvent event) {
        super(event);
        this.loseLevelOnDeath = event.definition().setting("lose-level-on-death", false);

        ConfigurationSection section = event.definition().settings().getConfigurationSection("levels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    levels.put(Integer.parseInt(key), section.getString(key, "").toLowerCase());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("El nivel '" + key + "' de gun-game no es un número.");
                }
            }
        }

        // Sin niveles escritos, la escalera de vanilla; con ellos, solo si se pide a
        // propósito. Así un servidor que se monta sus kits no se los ve ignorados por
        // haber dejado una clave puesta.
        if (event.definition().setting("vanilla", levels.isEmpty())) {
            buildLadder();
        }
        if (levels.isEmpty()) {
            plugin.getLogger().warning("El evento '" + event.definition().id()
                    + "' no tiene niveles configurados: todos jugarán con el kit normal.");
        }
    }

    private void buildLadder() {
        levels.clear();
        int wanted = event.definition().setting("vanilla-levels", 20);
        String food = event.definition().setting("vanilla-food", "cooked_beef;4");

        int level = 0;
        for (VanillaLadder.Rung rung : VanillaLadder.build(wanted, food)) {
            level++;
            ladder.put(level, rung);
            levels.put(level, rung.kit().id());
        }
        plugin.getLogger().info("El evento '" + event.definition().id() + "' usa la escalera de "
                + "vanilla del gun game: " + level + " nivel(es).");
    }

    @Override
    public String id() {
        return "gun-game";
    }

    @Override
    public void onStart() {
        for (Participant participant : event.participants()) {
            participant.setCounter(LEVEL, firstLevel());
        }
    }

    @Override
    public void onJoin(Player player) {
        Participant participant = participant(player);
        if (participant != null) {
            participant.setCounter(LEVEL, firstLevel());
        }
    }

    @Override
    public void onKill(Player killer, Player victim) {
        Participant participant = participant(killer);
        if (participant == null) {
            return;
        }
        plugin.stats().add(killer.getUniqueId(), Stat.GUNGAME_KILLS, 1);
        if (levels.isEmpty()) {
            return;
        }

        int current = participant.counterOr(LEVEL, firstLevel());
        Integer next = levels.higherKey(current);
        if (next == null) {
            // Estaba en el último peldaño y ha vuelto a matar: se acabó la escalera.
            plugin.stats().add(killer.getUniqueId(), Stat.GUNGAME_WINS, 1);
            event.finish(killer.getUniqueId());
            return;
        }

        participant.setCounter(LEVEL, next);
        // El evento vuelve a vestirlo entero: kit del nivel nuevo, efectos del rol y lo
        // que el modo añada. Repartir aquí solo el kit dejaba al jugador sin los efectos
        // de events.yml, porque entregarlo empieza por limpiárselos.
        event.redress(killer);

        plugin.messages().send(killer, "gungame.level-up", event.data(Map.of(
                "level", String.valueOf(next),
                "total", String.valueOf(levels.lastKey()),
                "kit", label(next))));

        if (next.equals(levels.lastKey())) {
            event.broadcast("gungame.final-level", event.data(Map.of("player", killer.getName())));
            event.sound("gungame-final");
        }
    }

    @Override
    public void onDeath(Player victim) {
        if (!loseLevelOnDeath) {
            return;
        }
        Participant participant = participant(victim);
        if (participant == null) {
            return;
        }
        Integer lower = levels.lowerKey(participant.counterOr(LEVEL, firstLevel()));
        if (lower != null) {
            participant.setCounter(LEVEL, lower);
            plugin.messages().send(victim, "gungame.level-down", event.data(Map.of(
                    "level", String.valueOf(lower),
                    "kit", label(lower))));
        }
    }

    @Override
    public String kitFor(Player player) {
        String kit = levels.get(levelOf(player));
        return kit == null || kit.isBlank() ? event.definition().kit("normal") : kit;
    }

    @Override
    public Kit kit(Player player) {
        VanillaLadder.Rung rung = ladder.get(levelOf(player));
        return rung != null ? rung.kit() : super.kit(player);
    }

    @Override
    public boolean usesKits() {
        return !ladder.isEmpty();
    }

    private int levelOf(Player player) {
        Participant participant = participant(player);
        return participant == null ? firstLevel() : participant.counterOr(LEVEL, firstLevel());
    }

    private int firstLevel() {
        return levels.isEmpty() ? 1 : levels.firstKey();
    }

    private String label(int level) {
        VanillaLadder.Rung rung = ladder.get(level);
        if (rung == null) {
            return levels.getOrDefault(level, "-");
        }
        List<String> names = plugin.messages().lines("gungame.ladder");
        return rung.step() < names.size() ? names.get(rung.step()) : String.valueOf(level);
    }

    @Override
    public Map<String, String> hudData(Player viewer) {
        Participant participant = participant(viewer);
        int level = levelOf(viewer);

        Map<String, String> data = new HashMap<>();
        data.put("level", String.valueOf(level));
        data.put("total", String.valueOf(levels.isEmpty() ? 1 : levels.lastKey()));
        data.put("kit", label(level));
        data.put("kills", String.valueOf(participant == null ? 0 : participant.kills()));
        return data;
    }

    @Override
    public Map<String, String> barData() {
        Participant best = null;
        for (Participant participant : event.participants()) {
            if (best == null || participant.counter(LEVEL) > best.counter(LEVEL)) {
                best = participant;
            }
        }
        return Map.of(
                "leader", best == null ? "-" : best.name(),
                "level", String.valueOf(best == null ? firstLevel() : best.counter(LEVEL)),
                "total", String.valueOf(levels.isEmpty() ? 1 : levels.lastKey()));
    }
}
