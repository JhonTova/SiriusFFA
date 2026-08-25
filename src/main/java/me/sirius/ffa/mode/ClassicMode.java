package me.sirius.ffa.mode;

import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class ClassicMode extends FFAMode {

    private final int killsToWin;

    public ClassicMode(FFAEvent event) {
        super(event);
        this.killsToWin = event.definition().setting("kills-to-win", 0);
    }

    @Override
    public String id() {
        return "classic";
    }

    @Override
    public void onKill(Player killer, Player victim) {
        Participant participant = participant(killer);
        if (participant == null) {
            return;
        }
        if (killsToWin > 0 && participant.kills() >= killsToWin) {
            event.finish(killer.getUniqueId());
            return;
        }
        if (killsToWin > 0 && participant.kills() == killsToWin - 1) {
            event.broadcast("classic.one-left", event.data(Map.of(
                    "player", killer.getName(),
                    "kills", String.valueOf(participant.kills()),
                    "target", String.valueOf(killsToWin))));
        }
    }

    @Override
    public Map<String, String> hudData(Player viewer) {
        Participant participant = participant(viewer);
        Map<String, String> data = new HashMap<>();
        data.put("kills", String.valueOf(participant == null ? 0 : participant.kills()));
        data.put("deaths", String.valueOf(participant == null ? 0 : participant.deaths()));
        data.put("target", killsToWin > 0 ? String.valueOf(killsToWin) : "-");
        data.put("leader", leaderName());
        data.put("remaining", remaining());
        return data;
    }

    @Override
    public Map<String, String> barData() {
        return Map.of(
                "leader", leaderName(),
                "remaining", remaining(),
                "target", killsToWin > 0 ? String.valueOf(killsToWin) : "-");
    }

    @Override
    public double barProgress() {
        int max = event.definition().maxTime();
        return max <= 0 ? 1D : (double) Math.max(0, max - event.elapsed()) / max;
    }

    private String remaining() {
        int max = event.definition().maxTime();
        return max <= 0 ? "-" : Durations.clock(Math.max(0, max - event.elapsed()));
    }

    private String leaderName() {
        var leader = event.leader();
        if (leader == null) {
            return "-";
        }
        Participant participant = event.participant(leader);
        if (participant == null) {
            return "-";
        }
        Player online = Bukkit.getPlayer(leader);
        return (online == null ? participant.name() : online.getName()) + " (" + participant.kills() + ")";
    }
}
