package me.sirius.ffa.command;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record CommandPolicy(Mode mode, Set<String> commands) {

    public enum Mode {
        ALLOW_ALL,
        WHITELIST,
        BLACKLIST,
        DENY_ALL
    }

    public static final CommandPolicy ALLOW_ALL = new CommandPolicy(Mode.ALLOW_ALL, Set.of());

    public static CommandPolicy read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Mode mode;
        try {
            mode = Mode.valueOf(section.getString("mode", "WHITELIST").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.WHITELIST;
        }

        List<String> raw = section.getStringList("allowed");
        if (raw.isEmpty()) {
            raw = section.getStringList("commands");
        }
        if (raw.isEmpty()) {
            raw = section.getStringList("denied");
        }

        Set<String> commands = new HashSet<>(raw.size());
        for (String entry : raw) {
            commands.add(normalize(entry));
        }
        return new CommandPolicy(mode, Set.copyOf(commands));
    }

    public boolean allows(String command) {
        String name = normalize(command);
        return switch (mode) {
            case ALLOW_ALL -> true;
            case DENY_ALL -> false;
            case WHITELIST -> commands.contains(name);
            case BLACKLIST -> !commands.contains(name);
        };
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String name = input.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        int space = name.indexOf(' ');
        if (space >= 0) {
            name = name.substring(0, space);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return name;
    }
}
