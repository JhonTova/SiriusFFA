package me.sirius.ffa.inventory;

import me.sirius.core.SiriusPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SnapshotStore {

    private final SiriusPlugin plugin;
    private final Path folder;
    private final Map<UUID, PlayerSnapshot> pending = new ConcurrentHashMap<>();

    public SnapshotStore(SiriusPlugin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath().resolve("inventarios");
    }

    public int loadPending() {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "No se pudo crear la carpeta de inventarios. El FFA no puede proteger nada.", e);
            return 0;
        }

        try (var files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(".yml")).forEach(path -> {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                PlayerSnapshot snapshot = PlayerSnapshot.read(yaml);
                if (snapshot == null) {
                    plugin.getLogger().warning("Inventario guardado ilegible: " + path.getFileName()
                            + ". Se conserva el fichero por si hay que recuperarlo a mano.");
                    return;
                }
                pending.put(snapshot.player(), snapshot);
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudieron leer los inventarios guardados", e);
        }
        return pending.size();
    }

    public CompletableFuture<Boolean> save(PlayerSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.write(yaml);
        String data = yaml.saveToString();

        pending.put(snapshot.player(), snapshot);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.scheduler().async(() -> {
            try {
                write(snapshot.player(), data);
                future.complete(true);
            } catch (IOException e) {
                pending.remove(snapshot.player());
                plugin.getLogger().log(Level.SEVERE,
                        "No se pudo guardar el inventario de " + snapshot.name(), e);
                future.complete(false);
            }
        });
        return future;
    }

    private void write(UUID uuid, String data) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(uuid + ".yml");
        Path temp = folder.resolve(uuid + ".yml.tmp");

        Files.writeString(temp, data, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean has(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public PlayerSnapshot peek(UUID uuid) {
        return pending.get(uuid);
    }

    public PlayerSnapshot take(UUID uuid) {
        PlayerSnapshot snapshot = pending.remove(uuid);
        if (snapshot != null) {
            delete(uuid);
        }
        return snapshot;
    }

    public void discard(UUID uuid) {
        pending.remove(uuid);
        delete(uuid);
    }

    private void delete(UUID uuid) {
        if (!plugin.isEnabled()) {
            discardNow(uuid);
            return;
        }
        plugin.scheduler().async(() -> {
            try {
                Files.deleteIfExists(folder.resolve(uuid + ".yml"));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "No se pudo borrar el respaldo de inventario de " + uuid, e);
            }
        });
    }

    public void saveNow(PlayerSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.write(yaml);
        pending.put(snapshot.player(), snapshot);
        try {
            write(snapshot.player(), yaml.saveToString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "No se pudo guardar el inventario de " + snapshot.name() + " al apagar", e);
        }
    }

    public void discardNow(UUID uuid) {
        pending.remove(uuid);
        deleteNow(uuid);
    }

    private void deleteNow(UUID uuid) {
        try {
            Files.deleteIfExists(folder.resolve(uuid + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo borrar el respaldo de " + uuid, e);
        }
    }

    public File folder() {
        return folder.toFile();
    }

    public int pendingCount() {
        return pending.size();
    }
}
