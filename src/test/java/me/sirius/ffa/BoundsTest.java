package me.sirius.ffa;

import me.sirius.ffa.arena.Bounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundsTest {

    private static final Bounds ARENA = new Bounds("mundo", 0, 60, 0, 10, 70, 10);

    @Test
    @DisplayName("el centro está dentro")
    void inside() {
        assertTrue(ARENA.contains("mundo", 5.0, 65.0, 5.0));
    }

    @Test
    @DisplayName("el bloque del máximo cuenta como dentro, hasta su cara exterior")
    void maximumBlockIsInside() {
        assertTrue(ARENA.contains("mundo", 10.5, 70.5, 10.5));
        assertTrue(ARENA.contains("mundo", 10.99, 70.99, 10.99));
        assertFalse(ARENA.contains("mundo", 11.0, 65.0, 5.0));
    }

    @Test
    @DisplayName("el bloque del mínimo también cuenta")
    void minimumBlockIsInside() {
        assertTrue(ARENA.contains("mundo", 0.0, 60.0, 0.0));
        assertFalse(ARENA.contains("mundo", -0.01, 60.0, 0.0));
    }

    @Test
    @DisplayName("otro mundo nunca está dentro, aunque coincidan las coordenadas")
    void otherWorld() {
        assertFalse(ARENA.contains("nether", 5.0, 65.0, 5.0));
    }

    @Test
    @DisplayName("guardar y leer devuelve el mismo cuboide")
    void roundTrip() {
        Bounds decoded = Bounds.decode(ARENA.encode());
        assertEquals(ARENA, decoded);
    }

    @Test
    @DisplayName("un texto roto no rompe la carga de la arena")
    void brokenData() {
        assertNull(Bounds.decode(""));
        assertNull(Bounds.decode(null));
        assertNull(Bounds.decode("mundo;0;60;0"));
        assertNull(Bounds.decode("mundo;a;b;c;d;e;f"));
    }

    @Test
    @DisplayName("el volumen cuenta los bloques de los bordes")
    void volume() {
        assertEquals(11L * 11L * 11L, ARENA.volume());
    }
}
