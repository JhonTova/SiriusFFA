package me.sirius.ffa;

import me.sirius.ffa.util.Durations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationsTest {

    @Test
    @DisplayName("las unidades se entienden sueltas y combinadas")
    void units() {
        assertEquals(600, Durations.seconds("10m", 0));
        assertEquals(90, Durations.seconds("90s", 0));
        assertEquals(3600, Durations.seconds("1h", 0));
        assertEquals(5400, Durations.seconds("1h30m", 0));
        assertEquals(86400, Durations.seconds("1d", 0));
    }

    @Test
    @DisplayName("un número suelto son segundos")
    void bareNumber() {
        assertEquals(30, Durations.seconds("30", 0));
    }

    @Test
    @DisplayName("lo que no se entiende cae al valor por defecto")
    void fallback() {
        assertEquals(600, Durations.seconds("", 600));
        assertEquals(600, Durations.seconds(null, 600));
        assertEquals(600, Durations.seconds("diez minutos", 600));
        assertEquals(600, Durations.seconds("10x", 600));
    }

    @Test
    @DisplayName("el reloj del HUD mantiene el ancho")
    void clock() {
        assertEquals("04:31", Durations.clock(271));
        assertEquals("00:09", Durations.clock(9));
        assertEquals("1:00:00", Durations.clock(3600));
        assertEquals("00:00", Durations.clock(-5));
    }

    @Test
    @DisplayName("el resumen del final del evento se lee en castellano")
    void words() {
        assertEquals("1 segundo", Durations.words(1));
        assertEquals("45 segundos", Durations.words(45));
        assertEquals("1 minuto", Durations.words(60));
        assertEquals("28 minutos", Durations.words(1680));
        assertEquals("1 hora y 30 min", Durations.words(5400));
    }
}
