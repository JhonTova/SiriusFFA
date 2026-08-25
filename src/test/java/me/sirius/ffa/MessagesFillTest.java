package me.sirius.ffa;

import me.sirius.ffa.config.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesFillTest {

    @Test
    @DisplayName("los marcadores se sustituyen")
    void replaces() {
        assertEquals("kills 12 de 30",
                Messages.fill("kills <kills> de <target>", Map.of("kills", "12", "target", "30")));
    }

    @Test
    @DisplayName("un marcador con color propio conserva sus etiquetas")
    void keepsColouredValues() {
        assertEquals("estado: <green>en vivo",
                Messages.fill("estado: <state>", Map.of("state", "<green>en vivo")));
    }

    @Test
    @DisplayName("lo que no se pasa se queda como estaba")
    void leavesUnknownPlaceholders() {
        assertEquals("ronda <round>", Messages.fill("ronda <round>", Map.of()));
    }

    @Test
    @DisplayName("un valor nulo no rompe el mensaje")
    void nullValue() {
        Map<String, String> data = new HashMap<>();
        data.put("player", null);
        assertEquals("gana ", Messages.fill("gana <player>", data));
    }
}
