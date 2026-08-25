package me.sirius.ffa;

import me.sirius.ffa.command.CommandPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPolicyTest {

    @Test
    @DisplayName("la lista blanca solo deja pasar lo suyo")
    void whitelist() {
        CommandPolicy policy = new CommandPolicy(CommandPolicy.Mode.WHITELIST, Set.of("spawn", "repair"));

        assertTrue(policy.allows("spawn"));
        assertTrue(policy.allows("/repair"));
        assertFalse(policy.allows("home"));
        assertFalse(policy.allows("/enderchest"));
    }

    @Test
    @DisplayName("la lista negra deja pasar todo lo demás")
    void blacklist() {
        CommandPolicy policy = new CommandPolicy(CommandPolicy.Mode.BLACKLIST, Set.of("home", "back"));

        assertFalse(policy.allows("home"));
        assertFalse(policy.allows("/back"));
        assertTrue(policy.allows("spawn"));
    }

    @Test
    @DisplayName("los extremos no miran la lista")
    void extremes() {
        assertTrue(CommandPolicy.ALLOW_ALL.allows("cualquiera"));
        assertFalse(new CommandPolicy(CommandPolicy.Mode.DENY_ALL, Set.of("spawn")).allows("spawn"));
    }

    @Test
    @DisplayName("el prefijo del plugin no cuela un comando bloqueado")
    void namespacedCommandsAreTheSameCommand() {
        CommandPolicy policy = new CommandPolicy(CommandPolicy.Mode.BLACKLIST, Set.of("home"));

        assertFalse(policy.allows("/essentials:home"));
        assertFalse(policy.allows("/home casa"));
    }

    @Test
    @DisplayName("normalizar quita barra, argumentos, prefijo y mayúsculas")
    void normalize() {
        assertEquals("spawn", CommandPolicy.normalize("/Spawn"));
        assertEquals("spawn", CommandPolicy.normalize("/essentials:spawn"));
        assertEquals("msg", CommandPolicy.normalize("/msg Jhon hola"));
        assertEquals("", CommandPolicy.normalize(null));
    }
}
