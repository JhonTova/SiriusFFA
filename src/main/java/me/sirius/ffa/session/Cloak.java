package me.sirius.ffa.session;

import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.kit.KitEffects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Invisibilidad de todos los que están dentro del FFA. Es un efecto de poción y nada
// más: no se toca ningún atributo del jugador ni se le monta un equipo de scoreboard,
// porque un jugador invisible ya no enseña su nombre y eso es justo lo que se busca.
// El efecto nunca se pone permanente. Dura como mucho lo que diga 'invisible.duration'
// y el reloj lo renueva mientras el jugador siga jugando, así que si alguien se cae del
// servidor sin pasar por la salida se le acaba solo en ese plazo en vez de quedarse
// invisible para siempre.
public final class Cloak {

    // Ticks que se dejan de margen: en cuanto al efecto le queda menos que esto, se
    // renueva. Así el jugador nunca parpadea y lo que le queda encima nunca pasa de la
    // duración configurada.
    private static final int MARGIN = 20 * 10;

    private final SiriusFFA plugin;

    private final Set<UUID> cloaked = new HashSet<>();

    // Quien ya venía invisible por su cuenta —se bebió una poción antes de entrar— se
    // queda como estaba al salir: se le guarda su efecto y se le devuelve.
    private final Map<UUID, PotionEffect> previous = new HashMap<>();

    private PotionEffectType invisibility;
    private boolean resolved;

    public Cloak(SiriusFFA plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.config().invisible();
    }

    // Repasa a todos los que están dentro. Es lo que hace el interruptor en caliente.
    public int refreshAll() {
        for (FFASession session : plugin.sessions().all()) {
            Player player = Bukkit.getPlayer(session.player());
            if (player != null) {
                refresh(player, session);
            }
        }
        return cloaked.size();
    }

    public void refresh(Player player) {
        refresh(player, plugin.sessions().of(player));
    }

    public void refresh(Player player, FFASession session) {
        if (session == null || !applies(session)) {
            clear(player);
            return;
        }
        apply(player);
    }

    private boolean applies(FFASession session) {
        return plugin.config().invisible() && covers(session);
    }

    // Si a esta sesión le tocaría el efecto con el interruptor encendido.
    public boolean covers(FFASession session) {
        if (!session.inEvent()) {
            return plugin.config().invisibleInArenas();
        }
        if (!plugin.config().invisibleInEvents()) {
            return false;
        }
        // Con el evento decidido todo el mundo vuelve a verse: el resumen y el viaje de
        // vuelta se ven mejor con la gente delante.
        FFAEvent event = plugin.events().active();
        return event != null && event.state().running();
    }

    private void apply(Player player) {
        PotionEffectType type = type();
        if (type == null) {
            return;
        }
        PotionEffect current = player.getPotionEffect(type);

        if (cloaked.add(player.getUniqueId()) && current != null) {
            previous.put(player.getUniqueId(), current);
        }
        if (current != null && current.getDuration() > MARGIN) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type,
                plugin.config().invisibleSeconds() * 20, 0, false, false, true));
    }

    public void clear(Player player) {
        if (!cloaked.remove(player.getUniqueId())) {
            return;
        }
        PotionEffectType type = type();
        if (type == null) {
            return;
        }
        player.removePotionEffect(type);

        PotionEffect own = previous.remove(player.getUniqueId());
        if (own != null) {
            player.addPotionEffect(own);
        }
    }

    // El reloj pasa por aquí una vez por segundo y por jugador: el efecto se busca en el
    // registro una sola vez y el aviso sale una sola vez.
    private PotionEffectType type() {
        if (!resolved) {
            resolved = true;
            invisibility = KitEffects.resolve("invisibility");
            if (invisibility == null) {
                plugin.getLogger().warning("Este servidor no conoce el efecto 'invisibility': "
                        + "la invisibilidad del FFA se queda apagada.");
            }
        }
        return invisibility;
    }
}
