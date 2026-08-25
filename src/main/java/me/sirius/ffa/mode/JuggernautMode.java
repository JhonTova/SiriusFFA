package me.sirius.ffa.mode;

import me.sirius.ffa.event.EventDefinition;
import me.sirius.ffa.event.EventState;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.kit.KitEffects;
import me.sirius.ffa.stats.Stat;
import me.sirius.ffa.util.Durations;
import me.sirius.ffa.util.Players;
import me.sirius.ffa.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class JuggernautMode extends FFAMode {

    private static final String SURVIVED = "juggernaut_survived";

    private static final String ROLE = "juggernaut";

    private final int survivalTime;
    private final int maxRounds;
    private final boolean preventConsecutive;
    private final PushStick push;

    private EventDefinition.Role fallback;

    private UUID juggernaut;
    private UUID previous;
    private int secondsLeft;
    private long roundStartedAt;

    public JuggernautMode(FFAEvent event) {
        super(event);
        this.survivalTime = event.definition().seconds("rounds.survival-time", 300);
        this.maxRounds = event.definition().setting("rounds.max-rounds", 5);
        this.preventConsecutive = event.definition().setting("prevent-consecutive-selection", true);
        this.push = PushStick.read(event, event.definition().settings().getConfigurationSection("push"));

        if (!event.definition().kit(ROLE).isBlank()) {
            plugin.getLogger().warning("El evento '" + event.definition().id() + "' declara "
                    + "'kits." + ROLE + "'. Ya no se usa: el juggernaut pelea con el mismo kit que "
                    + "los demás y lo que le hace fuerte son los efectos de 'effects." + ROLE
                    + "'. Puedes borrar esa línea de events.yml.");
        }
    }

    @Override
    public String id() {
        return "juggernaut";
    }

    @Override
    public void onStart() {
        startRound();
    }

    @Override
    public void onSecond() {
        if (juggernaut == null) {
            startRound();
            return;
        }
        if (--secondsLeft > 0) {
            warn();
            return;
        }
        extracted();
    }

    private void extracted() {
        Player survivor = Bukkit.getPlayer(juggernaut);
        String name = survivor != null ? survivor.getName() : nameOf(juggernaut);

        Map<String, String> values = event.data(Map.of(
                "player", name,
                "time", Durations.words(survivalTime),
                "rounds", rounds()));

        event.broadcast("juggernaut.survived", values);
        event.broadcastTitle("juggernaut.survived-title", "juggernaut.survived-subtitle", values);
        event.sound("juggernaut-extracted");

        plugin.stats().add(juggernaut, Stat.JUGGERNAUT_WINS, 1);
        event.finish(juggernaut);
    }

    private void warn() {
        if (secondsLeft <= 5) {
            Map<String, String> values = event.data(Map.of(
                    "player", nameOf(juggernaut),
                    "seconds", String.valueOf(secondsLeft)));
            event.broadcastTitle("juggernaut.final-title", "juggernaut.final-subtitle", values);
            event.sound("countdown");
            return;
        }
        if (secondsLeft % 60 != 0 && secondsLeft != 30 && secondsLeft != 10) {
            return;
        }
        event.broadcast("juggernaut.time-left", event.data(Map.of(
                "player", nameOf(juggernaut),
                "time", Durations.clock(secondsLeft),
                "seconds", String.valueOf(secondsLeft),
                "hearts", hearts())));
        if (secondsLeft <= 30) {
            event.sound("countdown");
        }
    }

    @Override
    public String kitFor(Player player) {
        return event.definition().kit("normal");
    }

    @Override
    public EventDefinition.Role defaultRole(String role) {
        if (!ROLE.equals(role)) {
            return EventDefinition.Role.NONE;
        }
        if (fallback == null) {
            fallback = new EventDefinition.Role(effects(
                    "resistance;0;0",
                    "strength;0;0",
                    "regeneration;0;0",
                    "speed;0;0",
                    "health_boost;0;4"), 0, true, true);
            plugin.getLogger().info("El evento '" + event.definition().id() + "' no configura "
                    + "'effects." + ROLE + "': el juggernaut sale con los efectos de serie "
                    + "(resistencia, fuerza, regeneración, velocidad y 10 corazones extra). "
                    + "Escribe 'effects." + ROLE + "' en events.yml para decidirlos tú.");
        }
        return fallback;
    }

    private static List<PotionEffect> effects(String... raw) {
        List<PotionEffect> parsed = new ArrayList<>();
        for (String line : raw) {
            PotionEffect effect = KitEffects.parse(line);
            if (effect != null) {
                parsed.add(effect);
            }
        }
        return List.copyOf(parsed);
    }

    @Override
    public void onEquip(Player player) {
        if (player.getUniqueId().equals(juggernaut)) {
            push.give(player);
        }
    }

    @Override
    public boolean onUse(Player player, ItemStack item) {
        if (!player.getUniqueId().equals(juggernaut) || !push.isPushStick(item)) {
            return false;
        }
        return push.use(player);
    }

    @Override
    public void onKill(Player killer, Player victim) {
        if (!victim.getUniqueId().equals(juggernaut)) {
            return;
        }
        plugin.stats().add(killer.getUniqueId(), Stat.JUGGERNAUT_KILLS, 1);
        event.broadcast("juggernaut.killed", event.data(Map.of(
                "player", victim.getName(),
                "killer", killer.getName(),
                "time", Durations.clock(roundElapsed()),
                "left", Durations.clock(Math.max(0, secondsLeft)))));
        event.sound("juggernaut-fallen");
        fallen();
    }

    @Override
    public void onDeath(Player victim) {
        if (victim.getUniqueId().equals(juggernaut)) {
            event.broadcast("juggernaut.died", event.data(Map.of(
                    "player", victim.getName(),
                    "time", Durations.clock(roundElapsed()),
                    "left", Durations.clock(Math.max(0, secondsLeft)))));
            event.sound("juggernaut-fallen");
            fallen();
        }
    }

    @Override
    public void onQuit(UUID player) {
        if (player.equals(juggernaut)) {
            event.broadcast("juggernaut.left", event.data(Map.of("player", nameOf(player))));
            fallen();
        }
    }

    private void fallen() {
        if (event.state() != EventState.ACTIVE) {
            // La partida ya está terminando: rotar ahora repartiría un kit a alguien a
            // quien se le está devolviendo el suyo.
            juggernaut = null;
            return;
        }
        if (juggernaut != null) {
            Participant participant = event.participant(juggernaut);
            if (participant != null) {
                participant.setCounter(SURVIVED, Math.max(participant.counter(SURVIVED), roundElapsed()));
            }
            plugin.stats().add(juggernaut, Stat.JUGGERNAUT_DEATHS, 1);
            previous = juggernaut;
        }
        juggernaut = null;

        if (maxRounds > 0 && event.round() >= maxRounds) {
            nobodyWins("juggernaut.max-rounds");
            return;
        }
        startRound();
    }

    private void startRound() {
        if (event.state() != EventState.ACTIVE) {
            return;
        }
        UUID chosen = pick();
        if (chosen == null) {
            nobodyWins("juggernaut.nobody-left");
            return;
        }
        juggernaut = chosen;
        secondsLeft = survivalTime;
        roundStartedAt = System.currentTimeMillis();
        push.reset();
        int round = event.nextRound();

        Player player = Bukkit.getPlayer(chosen);
        if (player != null) {
            // Por aquí pasa todo: kit, efectos del rol (fuerza, resistencia, vida extra,
            // brillo) y punto de aparición.
            event.equip(player);
            // Y la vara, otra vez y a la vista: 'equip' ya se la da al vestirlo, pero si
            // el evento no reparte kits no pasa por ahí, y un juggernaut sin vara no
            // tiene con qué quitarse a nadie de encima. Darla dos veces no la duplica.
            push.give(player);
        }

        // El anterior vuelve a ser uno más: kit normal y, sobre todo, sin el brillo ni
        // la vida extra del rol, que se van al limpiarle los efectos.
        if (previous != null) {
            Player old = Bukkit.getPlayer(previous);
            if (old != null && event.has(previous)) {
                event.equip(old);
                push.take(old);
            }
        }

        plugin.stats().add(chosen, Stat.JUGGERNAUT_ROUNDS, 1);

        Map<String, String> values = event.data(Map.of(
                "player", nameOf(chosen),
                "round", String.valueOf(round),
                "rounds", rounds(),
                "time", Durations.words(survivalTime),
                "alive", String.valueOf(event.alive().size())));

        event.broadcast("juggernaut.new", values);
        event.sound("juggernaut-new");

        // Cada uno se entera de lo suyo: el elegido de que le toca aguantar, y el resto
        // de a quién tienen que ir a buscar.
        for (Player viewer : event.players()) {
            boolean isJuggernaut = viewer.getUniqueId().equals(chosen);
            plugin.messages().title(viewer,
                    isJuggernaut ? "juggernaut.chosen-title" : "juggernaut.hunt-title",
                    isJuggernaut ? "juggernaut.chosen-subtitle" : "juggernaut.hunt-subtitle",
                    values);
        }

        plugin.getLogger().info("Juggernaut de la ronda " + round + ": " + nameOf(chosen));
    }

    private void nobodyWins(String reasonKey) {
        Participant best = bestSurvivor();
        event.broadcast(reasonKey, event.data(Map.of(
                "rounds", rounds(),
                "best", best == null ? plugin.messages().get("event.no-winner") : best.name(),
                "time", Durations.words(best == null ? 0 : best.counter(SURVIVED)))));
        event.finish(null);
    }

    private UUID pick() {
        List<UUID> candidates = new ArrayList<>();
        for (Participant participant : event.alive()) {
            candidates.add(participant.player());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (preventConsecutive && previous != null && candidates.size() > 1) {
            candidates.remove(previous);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    @Override
    public void onTimeUp() {
        nobodyWins("juggernaut.time-up");
    }

    @Override
    public void onLastStanding(UUID last) {
        nobodyWins("juggernaut.nobody-left");
    }

    private Participant bestSurvivor() {
        Participant best = null;
        for (Participant participant : event.participants()) {
            if (best == null || participant.counter(SURVIVED) > best.counter(SURVIVED)) {
                best = participant;
            }
        }
        return best == null || best.counter(SURVIVED) <= 0 ? null : best;
    }

    private int roundElapsed() {
        return roundStartedAt == 0 ? 0 : (int) ((System.currentTimeMillis() - roundStartedAt) / 1000L);
    }

    private String hearts() {
        Player online = juggernaut == null ? null : Bukkit.getPlayer(juggernaut);
        if (online == null) {
            return "-";
        }
        return (int) Math.ceil(online.getHealth() / 2D)
                + "/" + (int) Math.ceil(Players.maxHealth(online) / 2D);
    }

    private String rounds() {
        return maxRounds > 0 ? String.valueOf(maxRounds) : "-";
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        Participant participant = event.participant(uuid);
        if (participant != null) {
            return participant.name();
        }
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? "-" : player.getName();
    }

    @Override
    public String role(Player player) {
        return player.getUniqueId().equals(juggernaut) ? ROLE : "normal";
    }

    @Override
    public Map<String, String> hudData(Player viewer) {
        Participant participant = participant(viewer);
        Map<String, String> data = new HashMap<>(barData());
        data.put("kills", String.valueOf(participant == null ? 0 : participant.kills()));
        data.put("role", viewer.getUniqueId().equals(juggernaut)
                ? plugin.messages().get("juggernaut.role-juggernaut")
                : plugin.messages().get("juggernaut.role-hunter"));
        return data;
    }

    @Override
    public Map<String, String> barData() {
        Map<String, String> data = new HashMap<>();
        data.put("juggernaut", nameOf(juggernaut));
        data.put("remaining", Durations.clock(Math.max(0, secondsLeft)));
        data.put("hearts", hearts());
        data.put("alive", String.valueOf(event.alive().size()));
        data.put("rounds", rounds());
        return data;
    }

    @Override
    public double barProgress() {
        return survivalTime <= 0 ? 1D : (double) Math.max(0, secondsLeft) / survivalTime;
    }

    @Override
    public String barColor() {
        if (juggernaut == null) {
            return "WHITE";
        }
        if (secondsLeft <= 30) {
            return "RED";
        }
        return secondsLeft <= 60 ? "YELLOW" : "GREEN";
    }

    public UUID juggernaut() {
        return juggernaut;
    }
}
