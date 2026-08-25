package me.sirius.ffa.reward;

import me.sirius.ffa.kit.KitEffects;
import me.sirius.ffa.util.Players;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public record KillReward(List<PotionEffect> effects, List<String> commands,
                         double heal, int food, boolean extinguish) {

    public static final KillReward NONE = new KillReward(List.of(), List.of(), 0D, 0, false);

    public static KillReward read(ConfigurationSection section) {
        if (section == null) {
            return NONE;
        }
        List<PotionEffect> effects = new ArrayList<>();

        ConfigurationSection list = section.getConfigurationSection("effects");
        if (list != null) {
            for (String name : list.getKeys(false)) {
                ConfigurationSection effect = list.getConfigurationSection(name);
                if (effect == null || !effect.getBoolean("enabled", true)) {
                    continue;
                }
                PotionEffect parsed = KitEffects.parse(name + ";"
                        + effect.getInt("duration", 3) + ";"
                        + effect.getInt("amplifier", 0));
                if (parsed != null) {
                    effects.add(parsed);
                }
            }
        }

        return new KillReward(
                List.copyOf(effects),
                List.copyOf(section.getStringList("commands")),
                section.getDouble("heal", 0D),
                section.getInt("food", 0),
                section.getBoolean("extinguish", false));
    }

    public boolean isEmpty() {
        return effects.isEmpty() && commands.isEmpty() && heal <= 0 && food <= 0 && !extinguish;
    }

    public void apply(Player killer, Player victim, Rewards rewards) {
        for (PotionEffect effect : effects) {
            killer.addPotionEffect(effect);
        }
        Players.heal(killer, heal);
        if (food > 0) {
            killer.setFoodLevel(Math.min(20, killer.getFoodLevel() + food));
        }
        if (extinguish) {
            killer.setFireTicks(0);
        }
        if (!commands.isEmpty()) {
            rewards.run(commands, killer, victim);
        }
    }
}
