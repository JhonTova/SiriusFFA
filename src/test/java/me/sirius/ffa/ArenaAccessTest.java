package me.sirius.ffa;

import me.sirius.ffa.arena.ArenaAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArenaAccessTest {

    @Test
    @DisplayName("open y closed, en ingles y en espanol")
    void parsesBothLanguages() {
        assertEquals(ArenaAccess.OPEN, ArenaAccess.parse("open", null));
        assertEquals(ArenaAccess.OPEN, ArenaAccess.parse("Abierta", null));
        assertEquals(ArenaAccess.CLOSED, ArenaAccess.parse("CLOSED", null));
        assertEquals(ArenaAccess.CLOSED, ArenaAccess.parse(" cerrada ", null));
        assertEquals(ArenaAccess.CLOSED, ArenaAccess.parse("evento", null));
    }

    @Test
    @DisplayName("lo que no se entiende se queda en el respaldo")
    void unknownFallsBack() {
        assertNull(ArenaAccess.parse("azul", null));
        assertEquals(ArenaAccess.OPEN, ArenaAccess.parse("", ArenaAccess.OPEN));
        assertEquals(ArenaAccess.CLOSED, ArenaAccess.parse(null, ArenaAccess.CLOSED));
    }

    @Test
    @DisplayName("cerrada es la que se reserva a los eventos")
    void closedIsReserved() {
        assertEquals(true, ArenaAccess.CLOSED.closed());
        assertEquals(true, ArenaAccess.OPEN.open());
    }
}
