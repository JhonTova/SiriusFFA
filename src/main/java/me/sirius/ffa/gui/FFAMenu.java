package me.sirius.ffa.gui;

import me.sirius.core.gui.Button;
import me.sirius.core.gui.Menu;
import me.sirius.core.item.ItemBuilder;
import me.sirius.ffa.SiriusFFA;
import me.sirius.ffa.arena.Arena;
import me.sirius.ffa.config.Messages;
import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.session.JoinResult;
import me.sirius.ffa.util.Durations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FFAMenu extends Menu {

    private static final int EVENT_SLOT = 40;

    private final SiriusFFA plugin;

    private FFAMenu(SiriusFFA plugin) {
        super(plugin.messages().get("gui.title"), 5);
        this.plugin = plugin;
    }

    public static void open(SiriusFFA plugin, Player player) {
        new FFAMenu(plugin).open(player);
    }

    @Override
    protected void build(Player viewer) {
        List<Arena> arenas = plugin.arenas().joinable();
        boolean locked = plugin.events().locksArenas();

        int slot = 10;
        for (Arena arena : arenas) {
            if (slot > 25) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 3;
            }
            set(slot++, arenaButton(arena, locked));
        }

        if (arenas.isEmpty()) {
            set(13, Button.display(ItemBuilder.of(Material.BARRIER)
                    .name(plugin.messages().get("gui.no-arenas"))
                    .lore(plugin.messages().lines("gui.no-arenas-lore"))
                    .build()));
        }

        set(EVENT_SLOT, eventButton());
        fill(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private Button arenaButton(Arena arena, boolean locked) {
        Map<String, String> data = new HashMap<>();
        data.put("arena", arena.displayName());
        data.put("players", String.valueOf(plugin.sessions().countIn(arena.id())));
        data.put("max", arena.maxPlayers() > 0 ? String.valueOf(arena.maxPlayers()) : "∞");
        data.put("kit", arena.kitEnabled() ? arena.kit() : plugin.messages().get("gui.no-kit"));

        String lore = locked
                ? "gui.arena-lore-locked"
                : arena.kitEnabled() ? "gui.arena-lore-kit" : "gui.arena-lore";

        ItemStack icon = ItemBuilder.of(locked ? Material.BARRIER : arena.icon())
                .name(plugin.messages().get("gui.arena-name", data))
                .lore(plugin.messages().lines(lore), data)
                .hideAll()
                .build();

        return Button.of(icon, player -> {
            player.closeInventory();
            JoinResult result = plugin.sessions().joinArena(player, arena);
            if (!result.ok()) {
                plugin.messages().send(player, result.message(), Map.of("arena", arena.displayName()));
            }
        });
    }

    private Button eventButton() {
        FFAEvent event = plugin.events().active();
        if (event == null) {
            return Button.display(ItemBuilder.of(Material.CLOCK)
                    .name(plugin.messages().get("gui.no-event"))
                    .lore(plugin.messages().lines("gui.no-event-lore"))
                    .build());
        }

        Map<String, String> data = new HashMap<>(event.data(Map.of()));
        data.put("state", plugin.messages().get("state." + event.state().name().toLowerCase()));
        data.put("countdown", Durations.clock(event.countdown()));

        // El lore se rellena antes de construirlo: el estado del evento ya viene con
        // color propio, y pasarlo como marcador lo dejaría en texto plano.
        List<String> lore = new ArrayList<>();
        for (String line : plugin.messages().lines("gui.event-lore")) {
            lore.add(Messages.fill(line, data));
        }

        ItemStack icon = ItemBuilder.of(Material.NETHER_STAR)
                .name(plugin.messages().get("gui.event-name", data))
                .lore(lore)
                .glow()
                .hideAll()
                .build();

        return Button.of(icon, player -> {
            player.closeInventory();
            JoinResult result = plugin.sessions().joinEvent(player);
            if (!result.ok()) {
                plugin.messages().send(player, result.message(), data);
            }
        });
    }
}
