package me.sirius.ffa.mode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushForceTest {

    @Test
    @DisplayName("de cerca empuja con toda la fuerza")
    void closeRange() {
        assertEquals(1.4D, PushStick.force(0D, 5D, 1.4D), 1.0E-6);
    }

    @Test
    @DisplayName("en el borde todavía empuja la mitad")
    void edge() {
        assertEquals(0.7D, PushStick.force(5D, 5D, 1.4D), 1.0E-6);
    }

    @Test
    @DisplayName("más lejos del radio no empuja menos que en el borde")
    void beyondRadius() {
        assertEquals(0.7D, PushStick.force(50D, 5D, 1.4D), 1.0E-6);
    }

    @Test
    @DisplayName("cuanto más cerca, más empuje")
    void decreasesWithDistance() {
        assertTrue(PushStick.force(1D, 5D, 1.4D) > PushStick.force(4D, 5D, 1.4D));
    }
}
