package me.sirius.ffa.mode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaLadderTest {

    @Test
    @DisplayName("la escalera completa son todos los peldaños en orden")
    void full() {
        int[] steps = VanillaLadder.steps(VanillaLadder.size());
        assertEquals(VanillaLadder.size(), steps.length);
        for (int i = 0; i < steps.length; i++) {
            assertEquals(i, steps[i]);
        }
    }

    @Test
    @DisplayName("con menos niveles se empieza abajo y se acaba arriba")
    void spread() {
        int[] steps = VanillaLadder.steps(10);
        assertEquals(10, steps.length);
        assertEquals(0, steps[0]);
        assertEquals(VanillaLadder.size() - 1, steps[steps.length - 1]);
    }

    @Test
    @DisplayName("los peldaños nunca van hacia atrás")
    void ascending() {
        for (int levels = 2; levels <= VanillaLadder.size(); levels++) {
            int[] steps = VanillaLadder.steps(levels);
            for (int i = 1; i < steps.length; i++) {
                assertTrue(steps[i] > steps[i - 1],
                        "con " + levels + " niveles se repite o baja el peldaño " + i);
            }
        }
    }

    @Test
    @DisplayName("pedir más niveles de los que hay no inventa ninguno")
    void clamped() {
        assertEquals(VanillaLadder.size(), VanillaLadder.steps(100).length);
        assertEquals(1, VanillaLadder.steps(0).length);
    }

    @Test
    @DisplayName("un solo nivel es el último, no el primero")
    void single() {
        assertEquals(VanillaLadder.size() - 1, VanillaLadder.steps(1)[0]);
    }
}
