package me.sirius.ffa.kit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitItemsTest {

    @Test
    @DisplayName("solo el material ya es un ítem")
    void materialOnly() {
        KitItems.Spec spec = KitItems.parse("stone_sword");
        assertEquals("stone_sword", spec.material());
        assertEquals(1, spec.amount());
        assertEquals(-1, spec.slot());
        assertTrue(spec.enchantments().isEmpty());
    }

    @Test
    @DisplayName("cantidad, hueco y encantamientos")
    void full() {
        KitItems.Spec spec = KitItems.parse("netherite_sword;1;0;sharpness:3,unbreaking:3");
        assertEquals("netherite_sword", spec.material());
        assertEquals(0, spec.slot());
        assertEquals(3, spec.enchantments().get("sharpness"));
        assertEquals(3, spec.enchantments().get("unbreaking"));
    }

    @Test
    @DisplayName("un campo vacío se queda con lo de siempre")
    void blankFields() {
        KitItems.Spec spec = KitItems.parse("iron_helmet;1;;protection:2");
        assertEquals(1, spec.amount());
        assertEquals(-1, spec.slot());
        assertEquals(2, spec.enchantments().get("protection"));
    }

    @Test
    @DisplayName("un encantamiento sin nivel es el nivel uno")
    void enchantWithoutLevel() {
        assertEquals(1, KitItems.parse("bow;1;0;power").enchantments().get("power"));
    }

    @Test
    @DisplayName("una cantidad que no es un número no rompe la línea")
    void brokenAmount() {
        KitItems.Spec spec = KitItems.parse("cooked_beef;muchas");
        assertEquals("cooked_beef", spec.material());
        assertEquals(1, spec.amount());
    }

    @Test
    @DisplayName("una línea vacía no es un ítem")
    void blank() {
        assertNull(KitItems.parse("   "));
        assertNull(KitItems.parse(null));
    }

    @Test
    @DisplayName("la cantidad se queda dentro de lo que cabe en un montón")
    void clampedAmount() {
        assertEquals(64, KitItems.parse("arrow;900").amount());
        assertEquals(1, KitItems.parse("arrow;0").amount());
    }
}
