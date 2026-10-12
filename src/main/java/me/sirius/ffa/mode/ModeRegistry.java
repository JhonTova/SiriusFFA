package me.sirius.ffa.mode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModeRegistry {

    private final Map<String, FFAMode.Factory> factories = new LinkedHashMap<>();

    public ModeRegistry() {
        register("classic", ClassicMode::new);
        register("juggernaut", JuggernautMode::new);
        register("gun-game", GunGameMode::new);
        register("one-in-the-chamber", OneInTheChamberMode::new);
        register("last-standing", LastStandingMode::new);
    }

    public void register(String id, FFAMode.Factory factory) {
        factories.put(normalize(id), factory);
    }

    public boolean has(String id) {
        return factories.containsKey(normalize(id));
    }

    public FFAMode.Factory factory(String id) {
        return factories.get(normalize(id));
    }

    public Set<String> ids() {
        return Set.copyOf(factories.keySet());
    }

    private static String normalize(String id) {
        if (id == null) {
            return "";
        }
        String clean = id.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(" ", "-");
        return switch (clean) {
            case "gungame" -> "gun-game";
            case "oitc", "oneinthechamber", "one-in-chamber" -> "one-in-the-chamber";
            case "jugger" -> "juggernaut";
            case "lastman", "last-man", "last-man-standing", "lastmanstanding", "laststanding",
                 "ultimo-en-pie", "battle-royale", "br" -> "last-standing";
            default -> clean;
        };
    }
}
