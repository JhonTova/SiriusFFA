package me.sirius.ffa.listener;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.event.EventState;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.reward.KillReward;
import me.sirius.ffa.session.FFASession;
import me.sirius.ffa.stats.Stat;
import me.sirius.ffa.util.Sounds;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.Objects;

public final class CombatListener implements Listener {

    private final SiriusFFA plugin;

    public CombatListener(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        FFASession victimSession = plugin.sessions().of(victim);
        FFASession attackerSession = attacker == null ? null : plugin.sessions().of(attacker);

        if (victimSession == null && attackerSession == null) {
            return;
        }
        if (attacker == null) {
            // Daño de una entidad que no es jugador: si la víctima está protegida al
            // aparecer, tampoco vale.
            if (victimSession != null && (victimSession.spectator() || victimSession.spawnProtected())) {
                event.setCancelled(true);
            }
            return;
        }
        if (victimSession == null || attackerSession == null || !sameGround(victimSession, attackerSession)) {
            event.setCancelled(true);
            return;
        }
        if (victimSession.spectator() || attackerSession.spectator()) {
            event.setCancelled(true);
            return;
        }
        if (victimSession.spawnProtected()) {
            event.setCancelled(true);
            plugin.messages().send(attacker, "combat.target-protected");
            return;
        }
        // Pegar renuncia a la protección propia: no se puede atacar desde ella.
        attackerSession.dropSpawnProtection();

        FFAEvent live = plugin.events().active();
        if (victimSession.inEvent()) {
            if (live == null || live.state() != EventState.ACTIVE) {
                event.setCancelled(true);
                return;
            }
            boolean projectile = event.getDamager() instanceof Projectile;
            event.setDamage(live.mode().damage(attacker, victim, event.getDamage(), projectile));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        FFASession session = plugin.sessions().of(player);
        if (session == null) {
            return;
        }
        if (session.spectator() || ending(session)) {
            event.setCancelled(true);
        }
    }

    private boolean ending(FFASession session) {
        if (!session.inEvent()) {
            return false;
        }
        FFAEvent live = plugin.events().active();
        return live != null && live.state() == EventState.ENDING;
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private boolean sameGround(FFASession first, FFASession second) {
        return first.arena().equals(second.arena())
                && Objects.equals(first.event(), second.event());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        FFASession session = plugin.sessions().of(victim);
        if (session == null) {
            return;
        }
        Arena arena = plugin.arenas().get(session.arena());

        // Sin esto el kit se quedaría tirado en el suelo de la arena, que es la vía
        // clásica para duplicar objetos en un minijuego.
        if (session.protectedInventory() || arena == null || !arena.keepDrops()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
        }
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        if (plugin.config().hideDeathMessages()) {
            event.setDeathMessage(null);
        }

        session.addDeath();
        plugin.stats().add(victim.getUniqueId(), Stat.DEATHS, 1);

        respawnLater(victim);

        Player killer = victim.getKiller();
        FFASession killerSession = killer == null ? null : plugin.sessions().of(killer);

        if (killer != null && killerSession != null && sameGround(session, killerSession)) {
            handleKill(killer, killerSession, victim, session, arena);
        } else {
            announceDeath(session, victim);
            if (session.inEvent() && plugin.events().active() != null) {
                Participant participant = plugin.events().active().participant(victim.getUniqueId());
                if (participant != null) {
                    participant.addDeath();
                }
                plugin.events().active().mode().onDeath(victim);
            }
        }
    }

    private void respawnLater(Player victim) {
        if (!plugin.config().instantRespawn()) {
            return;
        }
        // Sin comprobar la sesión: si el evento termina en el mismo golpe que lo mata,
        // seguiría muerto mirando la pantalla con la sesión ya cerrada.
        plugin.scheduler().syncLater(() -> {
            if (victim.isOnline() && victim.isDead()) {
                victim.spigot().respawn();
            }
        }, plugin.config().respawnDelay());
    }

    private void handleKill(Player killer, FFASession killerSession,
                            Player victim, FFASession victimSession, Arena arena) {
        killerSession.addKill();
        plugin.stats().add(killer.getUniqueId(), Stat.KILLS, 1);
        plugin.stats().max(killer.getUniqueId(), Stat.BEST_STREAK, killerSession.streak());

        reward(arena, victimSession).apply(killer, victim, plugin.rewards());
        Sounds.play(killer, plugin.config().cue("kill"));
        Sounds.play(victim, plugin.config().cue("death"));

        Map<String, String> data = Map.of(
                "killer", killer.getName(),
                "victim", victim.getName(),
                "streak", String.valueOf(killerSession.streak()),
                "kills", String.valueOf(killerSession.kills()));

        FFAEvent live = plugin.events().active();
        if (victimSession.inEvent() && live != null) {
            Participant killerParticipant = live.participant(killer.getUniqueId());
            Participant victimParticipant = live.participant(victim.getUniqueId());
            if (killerParticipant != null) {
                killerParticipant.addKill();
            }
            if (victimParticipant != null) {
                victimParticipant.addDeath();
            }
            live.broadcast("combat.kill", live.data(data));
            live.mode().onKill(killer, victim);
            return;
        }

        for (java.util.UUID uuid : plugin.sessions().playersIn(victimSession.arena())) {
            Player watcher = org.bukkit.Bukkit.getPlayer(uuid);
            if (watcher != null) {
                plugin.messages().send(watcher, "combat.kill", data);
            }
        }
    }

    private void announceDeath(FFASession session, Player victim) {
        Map<String, String> data = Map.of("victim", victim.getName());
        FFAEvent live = plugin.events().active();

        if (session.inEvent() && live != null) {
            live.broadcast("combat.death", live.data(data));
            return;
        }
        for (java.util.UUID uuid : plugin.sessions().playersIn(session.arena())) {
            Player watcher = org.bukkit.Bukkit.getPlayer(uuid);
            if (watcher != null) {
                plugin.messages().send(watcher, "combat.death", data);
            }
        }
    }

    private KillReward reward(Arena arena, FFASession session) {
        FFAEvent live = plugin.events().active();
        if (session.inEvent() && live != null && !live.definition().kill().isEmpty()) {
            return live.definition().kill();
        }
        if (arena != null && !arena.killReward().isEmpty()) {
            return arena.killReward();
        }
        return plugin.config().defaultKill();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        FFASession session = plugin.sessions().of(player);

        if (session == null) {
            // Murió justo cuando salía del FFA: el viaje de vuelta quedó pendiente porque
            // a un muerto no se le puede teletransportar.
            Location back = plugin.sessions().consumeReturn(player.getUniqueId());
            if (back != null) {
                event.setRespawnLocation(back);
            }
            return;
        }
        Location spawn = plugin.sessions().respawnLocation(session, player.getLocation());
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        // El kit se repone un tick después: durante el evento de respawn el jugador
        // todavía no está en el mundo nuevo y tocarle el inventario aquí se pierde.
        plugin.scheduler().syncLater(() -> {
            if (player.isOnline() && plugin.sessions().inFFA(player.getUniqueId())) {
                plugin.sessions().respawn(player);
            }
        }, plugin.config().respawnDelay());
    }
}
