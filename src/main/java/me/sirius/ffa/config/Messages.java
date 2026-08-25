package me.sirius.ffa.config;

import me.sirius.core.config.Config;
import me.sirius.core.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

public final class Messages {

    private final Config file;
    private String prefix = "";

    public Messages(Plugin plugin) {
        this.file = new Config(plugin, "messages.yml");
        reload();
    }

    public void reload() {
        file.reload();
        this.prefix = file.string("prefix", "");
    }

    public String get(String key) {
        String raw = file.string(key, key);
        return raw.contains("<prefix>") ? raw.replace("<prefix>", prefix) : raw;
    }

    public String get(String key, Map<String, String> placeholders) {
        return fill(get(key), placeholders);
    }

    public List<String> lines(String key) {
        List<String> raw = file.strings(key);
        if (raw.isEmpty()) {
            return List.of();
        }
        return raw.stream().map(line -> line.replace("<prefix>", prefix)).toList();
    }

    public void send(CommandSender target, String key) {
        String message = get(key);
        if (!message.isBlank()) {
            Text.send(target, message);
        }
    }

    public void send(CommandSender target, String key, Map<String, String> placeholders) {
        String message = get(key);
        if (!message.isBlank()) {
            Text.send(target, message, placeholders);
        }
    }

    public void sendLines(CommandSender target, String key) {
        for (String line : lines(key)) {
            Text.send(target, line);
        }
    }

    public void sendLines(CommandSender target, String key, Map<String, String> placeholders) {
        for (String line : lines(key)) {
            Text.send(target, line, placeholders);
        }
    }

    public void actionBar(Player player, String key, Map<String, String> placeholders) {
        String message = get(key);
        if (!message.isBlank()) {
            Text.actionBar(player, fill(message, placeholders));
        }
    }

    public void title(Player player, String titleKey, String subtitleKey, Map<String, String> data) {
        String title = get(titleKey);
        String subtitle = get(subtitleKey);
        if (title.isBlank() && subtitle.isBlank()) {
            return;
        }
        Text.title(player, fill(title, data), fill(subtitle, data), 5, 40, 10);
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        String message = get(key);
        if (!message.isBlank()) {
            Text.broadcast(fill(message, placeholders));
        }
    }

    public void sendRich(CommandSender target, String key, Map<String, String> placeholders) {
        String message = get(key);
        if (!message.isBlank()) {
            Text.send(target, fill(message, placeholders));
        }
    }

    public void sendRichLines(CommandSender target, String key, Map<String, String> placeholders) {
        for (String line : lines(key)) {
            Text.send(target, fill(line, placeholders));
        }
    }

    public static String fill(String message, Map<String, String> placeholders) {
        String out = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue();
            out = out.replace("<" + entry.getKey() + ">", value == null ? "" : value);
        }
        return out;
    }

    public String prefix() {
        return prefix;
    }

    public Config file() {
        return file;
    }
}
