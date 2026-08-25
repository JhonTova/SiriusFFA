package me.sirius.ffa.mode;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.event.EventDefinition;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.kit.Kit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public abstract class FFAMode {

    protected final FFAEvent event;
    protected final SiriusFFA plugin;

    protected FFAMode(FFAEvent event) {
        this.event = event;
        this.plugin = event.plugin();
    }

    public abstract String id();

    public void onStart() {
    }

    public void onJoin(Player player) {
    }

    public void onQuit(UUID player) {
    }

    public void onKill(Player killer, Player victim) {
    }

    public void onDeath(Player victim) {
    }

    public void onRespawn(Player player) {
    }

    public void onEquip(Player player) {
    }

    public boolean onUse(Player player, ItemStack item) {
        return false;
    }

    public void onSecond() {
    }

    public void onTimeUp() {
        event.finish(event.leader());
    }

    public void onLastStanding(UUID last) {
        event.finish(last);
    }

    public void onEnd() {
    }

    public double damage(Player attacker, Player victim, double base, boolean projectile) {
        return base;
    }

    public String role(Player player) {
        return "normal";
    }

    public EventDefinition.Role defaultRole(String role) {
        return EventDefinition.Role.NONE;
    }

    public String kitFor(Player player) {
        return event.definition().kit(role(player));
    }

    public Kit kit(Player player) {
        return plugin.kits().get(kitFor(player));
    }

    public boolean usesKits() {
        return false;
    }

    public Map<String, String> hudData(Player viewer) {
        return Map.of();
    }

    public Map<String, String> barData() {
        return Map.of();
    }

    public double barProgress() {
        return -1D;
    }

    public String barColor() {
        return null;
    }

    public String displayName() {
        return event.definition().displayName();
    }

    protected Participant participant(Player player) {
        return event.participant(player.getUniqueId());
    }

    @FunctionalInterface
    public interface Factory {
        FFAMode create(FFAEvent event);
    }
}
