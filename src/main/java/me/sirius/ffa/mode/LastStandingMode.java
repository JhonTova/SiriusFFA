package me.sirius.ffa.mode;

import me.sirius.ffa.event.EventState;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.kit.KitEffects;
import me.sirius.ffa.stats.Stat;
import me.sirius.ffa.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LastStandingMode extends FFAMode {

    private static final String LIVES = "last_standing_lives";
    private static final String PLACE = "last_standing_place";

    private final int lives;
    private final int revealAfter;
    private final int announceFrom;
    private final boolean killsBreakTie;

    private int started;
    private int announced = Integer.MAX_VALUE;
    private boolean revealed;

    public LastStandingMode(FFAEvent event) {
        super(event);
        this.lives = Math.max(1, event.definition().setting("lives", 1));
        this.revealAfter = Math.max(0, event.definition().seconds("reveal-after", 0));
        this.announceFrom = Math.max(0, event.definition().setting("announce-from", 3));
        this.killsBreakTie = event.definition().setting("time-up-winner-by-kills", true);
    }

    @Override
    public String id() {
        return "last-standing";
    }

    @Override
    public void onStart() {
        started = event.size();
        for (Participant participant : event.participants()) {
            participant.setCounter(LIVES, lives);
        }
        Map<String, String> values = event.data(Map.of(
                "players", String.valueOf(started),
                "lives", String.valueOf(lives)));

        event.broadcast(lives > 1 ? "laststanding.rules-lives" : "laststanding.rules", values);
        event.broadcastTitle("laststanding.start-title", "laststanding.start-subtitle", values);
    }

    @Override
    public void onJoin(Player player) {
        Participant participant = participant(player);
        if (participant != null) {
            participant.setCounter(LIVES, lives);
        }
    }

    @Override
    public void onKill(Player killer, Player victim) {
        plugin.stats().add(killer.getUniqueId(), Stat.LASTSTANDING_KILLS, 1);
        down(victim, killer);
    }

    @Override
    public void onDeath(Player victim) {
        down(victim, null);
    }

    private void down(Player victim, Player killer) {
        if (event.state() != EventState.ACTIVE) {
            return;
        }
        Participant participant = participant(victim);
        if (participant == null || participant.eliminated()) {
            return;
        }
        int left = participant.counterOr(LIVES, lives) - 1;
        participant.setCounter(LIVES, Math.max(0, left));

        if (left > 0) {
            plugin.messages().send(victim, "laststanding.lives-left", event.data(Map.of(
                    "lives", String.valueOf(left))));
            return;
        }
        out(victim, participant, killer);
    }

    private void out(Player victim, Participant participant, Player killer) {
        // El puesto se cuenta antes de eliminarlo: el que cae quedando tres es el tercero.
        int place = event.alive().size();
        participant.setCounter(PLACE, place);

        Map<String, String> values = event.data(Map.of(
                "player", victim.getName(),
                "killer", killer == null ? plugin.messages().get("placeholders.empty") : killer.getName(),
                "place", String.valueOf(place),
                "alive", String.valueOf(place - 1),
                "total", String.valueOf(total()),
                "kills", String.valueOf(participant.kills())));

        event.broadcast(killer == null ? "laststanding.fell" : "laststanding.eliminated", values);
        plugin.messages().title(victim, "laststanding.out-title", "laststanding.out-subtitle", values);
        event.sound("laststanding-out");

        // Aquí es donde pasa a espectador. Si el evento no los admite se le saca y se le
        // devuelve lo suyo, y de esa salida sale un remove() que ya mira si queda uno.
        event.eliminate(victim.getUniqueId());
        check();
    }

    private void check() {
        if (event.state() != EventState.ACTIVE) {
            return;
        }
        List<Participant> alive = event.alive();
        if (alive.size() > 1) {
            announce(alive.size());
            return;
        }
        win(alive.isEmpty() ? null : alive.get(0));
    }

    private void announce(int alive) {
        if (announceFrom <= 0 || alive > announceFrom || alive >= announced) {
            return;
        }
        announced = alive;
        Map<String, String> values = event.data(Map.of("alive", String.valueOf(alive)));

        event.broadcast("laststanding.remaining", values);
        if (alive == 2) {
            event.broadcastTitle("laststanding.duel-title", "laststanding.duel-subtitle", values);
            event.sound("laststanding-duel");
        }
    }

    private void win(Participant champion) {
        if (champion == null) {
            event.broadcast("laststanding.nobody", event.data(Map.of()));
            event.finish(null);
            return;
        }
        champion.setCounter(PLACE, 1);
        plugin.stats().add(champion.player(), Stat.LASTSTANDING_WINS, 1);

        event.broadcast("laststanding.winner", event.data(Map.of(
                "player", champion.name(),
                "kills", String.valueOf(champion.kills()),
                "total", String.valueOf(total()),
                "duration", Durations.words(event.elapsed()))));
        event.finish(champion.player());
    }

    @Override
    public void onLastStanding(UUID last) {
        win(last == null ? null : event.participant(last));
    }

    @Override
    public void onTimeUp() {
        List<Participant> alive = event.alive();
        if (alive.size() == 1) {
            win(alive.get(0));
            return;
        }
        Participant best = killsBreakTie ? best(alive) : null;
        if (best == null) {
            event.broadcast("laststanding.time-up-none", event.data(Map.of(
                    "alive", String.valueOf(alive.size()))));
            event.finish(null);
            return;
        }
        event.broadcast("laststanding.time-up", event.data(Map.of(
                "player", best.name(),
                "kills", String.valueOf(best.kills()),
                "alive", String.valueOf(alive.size()))));
        win(best);
    }

    // Empatados a kills no gana ninguno: esto se gana quedándose solo, y desempatar por
    // cualquier otra cosa acabaría coronando al que entró antes en la lista.
    private Participant best(List<Participant> alive) {
        Participant best = null;
        boolean tied = false;
        for (Participant participant : alive) {
            if (best == null || participant.kills() > best.kills()) {
                best = participant;
                tied = false;
            } else if (participant.kills() == best.kills()) {
                tied = true;
            }
        }
        return best == null || tied || best.kills() <= 0 ? null : best;
    }

    @Override
    public void onSecond() {
        if (revealAfter <= 0 || revealed || event.elapsed() < revealAfter) {
            return;
        }
        revealed = true;
        event.broadcast("laststanding.reveal", event.data(Map.of()));
        event.sound("laststanding-reveal");

        for (Participant participant : event.alive()) {
            Player player = Bukkit.getPlayer(participant.player());
            if (player != null) {
                reveal(player);
            }
        }
    }

    @Override
    public void onRespawn(Player player) {
        if (revealed) {
            // Vestirse de nuevo borra los efectos: sin esto, el que reaparece después del
            // destape sería el único que no brilla.
            reveal(player);
        }
    }

    private void reveal(Player player) {
        PotionEffect glow = KitEffects.parse("glowing;0;0");
        if (glow != null) {
            player.addPotionEffect(glow);
        }
    }

    @Override
    public Map<String, String> hudData(Player viewer) {
        Participant participant = participant(viewer);
        Map<String, String> data = new HashMap<>(barData());
        data.put("lives", String.valueOf(participant == null
                ? lives : participant.counterOr(LIVES, lives)));
        data.put("kills", String.valueOf(participant == null ? 0 : participant.kills()));
        data.put("place", participant == null || participant.counter(PLACE) <= 0
                ? "-" : "#" + participant.counter(PLACE));
        return data;
    }

    @Override
    public Map<String, String> barData() {
        int alive = event.alive().size();
        return Map.of(
                "alive", String.valueOf(alive),
                "total", String.valueOf(total()),
                "out", String.valueOf(Math.max(0, total() - alive)));
    }

    @Override
    public double barProgress() {
        int total = total();
        return total <= 0 ? 1D : (double) event.alive().size() / total;
    }

    @Override
    public String barColor() {
        int alive = event.alive().size();
        if (alive <= 2) {
            return "RED";
        }
        return alive <= 4 ? "YELLOW" : "GREEN";
    }

    // Los que empezaron. Se mira también el tamaño actual porque hasta que arranca la
    // partida no hay nada guardado.
    private int total() {
        return Math.max(started, event.size());
    }
}
