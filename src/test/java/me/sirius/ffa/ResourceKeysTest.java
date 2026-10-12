package me.sirius.ffa;

import me.sirius.ffa.mode.ModeRegistry;
import me.sirius.ffa.mode.VanillaLadder;
import me.sirius.ffa.util.Durations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceKeysTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    @DisplayName("messages.yml trae todos los textos que el código envía")
    void messages() throws IOException {
        Map<String, Object> file = load("messages.yml");
        for (String key : List.of(
                "event.no-winner",
                "event.pvp-off",
                "event.return-countdown",
                "event.cancelled",
                "event.finished-broadcast", "event.finished-title", "event.finished-subtitle",
                "event.summary",
                "event.finished-broadcast-none", "event.finished-title-none",
                "event.finished-subtitle-none", "event.summary-none",
                "juggernaut.new",
                "juggernaut.chosen-title", "juggernaut.chosen-subtitle",
                "juggernaut.hunt-title", "juggernaut.hunt-subtitle",
                "juggernaut.killed", "juggernaut.died", "juggernaut.left",
                "juggernaut.time-left", "juggernaut.final-title", "juggernaut.final-subtitle",
                "juggernaut.survived", "juggernaut.survived-title", "juggernaut.survived-subtitle",
                "juggernaut.max-rounds", "juggernaut.time-up", "juggernaut.nobody-left",
                "juggernaut.role-juggernaut", "juggernaut.role-hunter",
                "gungame.level-up", "gungame.level-down", "gungame.final-level",
                "laststanding.rules", "laststanding.rules-lives",
                "laststanding.start-title", "laststanding.start-subtitle",
                "laststanding.lives-left",
                "laststanding.eliminated", "laststanding.fell",
                "laststanding.out-title", "laststanding.out-subtitle",
                "laststanding.remaining",
                "laststanding.duel-title", "laststanding.duel-subtitle",
                "laststanding.reveal", "laststanding.winner", "laststanding.nobody",
                "laststanding.time-up", "laststanding.time-up-none",
                "bar.waiting", "bar.starting", "bar.ending",
                "bar.juggernaut", "bar.classic", "bar.gun-game", "bar.one-in-the-chamber",
                "bar.last-standing",
                "hud.last-standing", "hud.last-standing-actionbar",
                "commands.usage-invisible",
                "invisible.current-on", "invisible.current-off",
                "invisible.turned-on", "invisible.turned-off",
                "invisible.player-on", "invisible.player-off",
                "invisible.where-both", "invisible.where-events", "invisible.where-arenas")) {
            assertNotNull(at(file, key), "falta " + key + " en messages.yml");
        }
    }

    @Test
    @DisplayName("la invisibilidad tiene un tope de duración y no viene encendida")
    void invisibility() throws IOException {
        Map<String, Object> file = load("config.yml");
        assertEquals(Boolean.FALSE, at(file, "invisible.enabled"),
                "la invisibilidad no puede venir encendida de serie");

        Object duration = at(file, "invisible.duration");
        assertTrue(duration instanceof String text && Durations.seconds(text, 0) > 0
                        && Durations.seconds(text, 0) <= 3600,
                "invisible.duration tiene que ser un plazo real y como mucho de una hora: "
                        + "es el tope de lo que le queda encima a quien se cae del servidor");
    }

    @Test
    @DisplayName("config.yml trae los sonidos que el código pide")
    void sounds() throws IOException {
        Map<String, Object> file = load("config.yml");
        for (String cue : List.of("kill", "death", "countdown", "juggernaut-new",
                "juggernaut-fallen", "juggernaut-extracted", "gungame-final",
                "laststanding-out", "laststanding-duel", "laststanding-reveal",
                "event-win", "event-end")) {
            assertNotNull(at(file, "sounds." + cue), "falta el sonido " + cue);
        }
    }

    @Test
    @DisplayName("cada kit al que apunta un evento existe en kits.yml")
    void kits() throws IOException {
        Map<String, Object> kits = load("kits.yml");
        Map<String, Object> events = load("events.yml");

        for (Map.Entry<String, Object> entry : section(events, "events").entrySet()) {
            Map<String, Object> event = asMap(entry.getValue());
            for (Object kit : section(event, "kits").values()) {
                assertNotNull(at(kits, "kits." + kit),
                        "el evento " + entry.getKey() + " usa el kit " + kit + ", que no existe");
            }
            for (Object kit : section(event, "levels").values()) {
                assertNotNull(at(kits, "kits." + kit),
                        "un nivel de " + entry.getKey() + " usa el kit " + kit + ", que no existe");
            }
        }
    }

    @Test
    @DisplayName("ningún evento le da kit propio al juggernaut")
    void juggernautHasNoKit() throws IOException {
        Map<String, Object> events = load("events.yml");
        for (Map.Entry<String, Object> entry : section(events, "events").entrySet()) {
            Map<String, Object> event = asMap(entry.getValue());
            assertNull(section(event, "kits").get("juggernaut"),
                    "el evento " + entry.getKey() + " declara un kit de juggernaut, y se ignora: "
                            + "el rol se configura en effects.juggernaut");
        }
    }

    @Test
    @DisplayName("el juggernaut lleva sus efectos escritos en events.yml")
    void juggernautHasEffects() throws IOException {
        Object effects = at(load("events.yml"), "events.juggernaut.effects.juggernaut");
        assertTrue(effects instanceof List<?> list && !list.isEmpty(),
                "effects.juggernaut no puede estar vacío: es lo único que hace fuerte al rol");
    }

    @Test
    @DisplayName("cada evento apunta a un modo que existe")
    void modes() throws IOException {
        ModeRegistry registry = new ModeRegistry();
        for (Map.Entry<String, Object> entry : section(load("events.yml"), "events").entrySet()) {
            Object mode = asMap(entry.getValue()).getOrDefault("mode", entry.getKey());
            assertTrue(registry.has(String.valueOf(mode)),
                    "el evento " + entry.getKey() + " usa el modo " + mode + ", que no existe");
        }
    }

    @Test
    @DisplayName("el ultimo en pie deja mirar a los eliminados")
    void lastStandingKeepsSpectators() throws IOException {
        assertEquals(Boolean.TRUE, at(load("events.yml"), "events.last-standing.spectators"),
                "sin espectadores el eliminado sale del evento y el modo se queda sin gracia");
        assertEquals(Boolean.TRUE, at(load("config.yml"), "spectator.enabled"),
                "spectator.enabled manda sobre el evento: en false nadie se queda mirando");
    }

    @Test
    @DisplayName("la escalera de vanilla tiene un nombre por peldaño en messages.yml")
    void ladder() throws IOException {
        Object names = at(load("messages.yml"), "gungame.ladder");
        assertTrue(names instanceof List<?> list && list.size() >= VanillaLadder.size(),
                "gungame.ladder necesita al menos " + VanillaLadder.size() + " nombres");
    }

    @Test
    @DisplayName("la espera del final da tiempo a que caduque la marca de combate")
    void endDelay() throws IOException {
        Map<String, Object> events = load("events.yml");
        for (Map.Entry<String, Object> entry : section(events, "events").entrySet()) {
            Object delay = at(asMap(entry.getValue()), "end-delay");
            assertTrue(delay instanceof Integer && (Integer) delay >= 30,
                    "el evento " + entry.getKey() + " saca a la gente demasiado pronto: "
                            + "con menos de 30s el plugin de combate la devuelve a la arena");
        }
    }

    private static Map<String, Object> load(String name) throws IOException {
        try (Reader reader = Files.newBufferedReader(RESOURCES.resolve(name), StandardCharsets.UTF_8)) {
            return new Yaml().load(reader);
        }
    }

    private static Object at(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private static Map<String, Object> section(Map<String, Object> root, String path) {
        Object value = at(root, path);
        return value instanceof Map ? asMap(value) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
}
