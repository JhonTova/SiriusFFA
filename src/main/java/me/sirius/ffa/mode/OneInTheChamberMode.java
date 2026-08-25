package me.sirius.ffa.mode;

import me.sirius.ffa.event.FFAEvent;
import me.sirius.ffa.event.Participant;
import me.sirius.ffa.stats.Stat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class OneInTheChamberMode extends FFAMode {

    private static final String LIVES = "oitc_lives";

    private final int startingLives;
    private final int startingAmmo;
    private final int ammoPerKill;
    private final boolean instantKill;
    private final double meleeDamage;
    private final Material ammoItem;

    public OneInTheChamberMode(FFAEvent event) {
        super(event);
        this.startingLives = Math.max(1, event.definition().setting("lives", 3));
        this.startingAmmo = Math.max(0, event.definition().setting("starting-ammo", 1));
        this.ammoPerKill = Math.max(0, event.definition().setting("ammo-per-kill", 1));
        this.instantKill = event.definition().setting("instant-kill-projectile", true);
        this.meleeDamage = event.definition().settings().getDouble("melee-damage", 2D);

        Material material = Material.matchMaterial(event.definition().setting("ammo-item", "ARROW"));
        this.ammoItem = material == null ? Material.ARROW : material;
    }

    @Override
    public String id() {
        return "one-in-the-chamber";
    }

    @Override
    public void onStart() {
        for (Participant participant : event.participants()) {
            participant.setCounter(LIVES, startingLives);
        }
        for (Player player : event.players()) {
            giveAmmo(player, startingAmmo);
        }
    }

    @Override
    public void onJoin(Player player) {
        Participant participant = participant(player);
        if (participant != null) {
            participant.setCounter(LIVES, startingLives);
        }
    }

    @Override
    public void onRespawn(Player player) {
        giveAmmo(player, startingAmmo);
    }

    @Override
    public void onKill(Player killer, Player victim) {
        plugin.stats().add(killer.getUniqueId(), Stat.OITC_KILLS, 1);
        giveAmmo(killer, ammoPerKill);
        loseLife(victim);
    }

    @Override
    public void onDeath(Player victim) {
        loseLife(victim);
    }

    private void loseLife(Player victim) {
        Participant participant = participant(victim);
        if (participant == null) {
            return;
        }
        int left = participant.counterOr(LIVES, startingLives) - 1;
        participant.setCounter(LIVES, Math.max(0, left));

        if (left > 0) {
            plugin.messages().send(victim, "oitc.lives-left", event.data(Map.of(
                    "lives", String.valueOf(left))));
            return;
        }
        event.broadcast("oitc.eliminated", event.data(Map.of("player", victim.getName())));
        event.eliminate(victim.getUniqueId());

        var alive = event.alive();
        if (alive.size() == 1) {
            plugin.stats().add(alive.get(0).player(), Stat.OITC_WINS, 1);
            event.finish(alive.get(0).player());
        }
    }

    @Override
    public double damage(Player attacker, Player victim, double base, boolean projectile) {
        if (projectile) {
            return instantKill ? 1000D : base;
        }
        return meleeDamage > 0 ? meleeDamage : base;
    }

    private void giveAmmo(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        // Lo que no cabe se descarta a propósito: en este modo la munición es el marcador,
        // y dejarla caer al suelo la convertiría en algo que se recoge por el mapa.
        player.getInventory().addItem(
                plugin.inventories().markLent(new ItemStack(ammoItem, amount)));
        player.updateInventory();
    }

    private int ammoOf(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == ammoItem) {
                total += item.getAmount();
            }
        }
        return total;
    }

    @Override
    public Map<String, String> barData() {
        return Map.of("alive", String.valueOf(event.alive().size()));
    }

    @Override
    public Map<String, String> hudData(Player viewer) {
        Participant participant = participant(viewer);
        Map<String, String> data = new HashMap<>();
        data.put("lives", String.valueOf(participant == null
                ? startingLives : participant.counterOr(LIVES, startingLives)));
        data.put("ammo", String.valueOf(ammoOf(viewer)));
        data.put("kills", String.valueOf(participant == null ? 0 : participant.kills()));
        data.put("alive", String.valueOf(event.alive().size()));
        return data;
    }
}
