package me.sirius.ffa.mode;

import me.sirius.ffa.kit.Kit;
import me.sirius.ffa.kit.KitItems;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class VanillaLadder {

    private record Step(String hand, String helmet, String chestplate, String leggings, String boots) {
    }

    private static final List<Step> STEPS = List.of(
            step(""),
            step("stick"),
            step("wooden_sword"),
            step("wooden_axe"),
            step("stone_sword", "leather_helmet", "", "", ""),
            step("stone_axe", "leather_helmet", "", "", "leather_boots"),
            step("bow|arrow;8", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
            step("iron_sword", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
            step("iron_axe", "chainmail_helmet", "", "", "chainmail_boots"),
            step("iron_sword;1;;sharpness:1",
                    "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"),
            step("bow;1;;power:1|arrow;16",
                    "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"),
            step("diamond_sword", "iron_helmet", "", "", "iron_boots"),
            step("diamond_axe", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
            step("diamond_sword;1;;sharpness:1",
                    "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
            step("diamond_sword;1;;sharpness:2", "diamond_helmet", "", "", "diamond_boots"),
            step("bow;1;;power:2|arrow;16",
                    "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"),
            step("diamond_sword;1;;sharpness:3,fire_aspect:1",
                    "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"),
            step("netherite_sword", "netherite_helmet", "", "", "netherite_boots"),
            step("netherite_sword;1;;sharpness:2",
                    "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"),
            step("netherite_sword;1;;sharpness:4,fire_aspect:2",
                    "netherite_helmet;1;;protection:2", "netherite_chestplate;1;;protection:2",
                    "netherite_leggings;1;;protection:2", "netherite_boots;1;;protection:2"));

    private static final int FOOD_SLOT = 8;

    private VanillaLadder() {
    }

    public record Rung(Kit kit, int step) {
    }

    public static int size() {
        return STEPS.size();
    }

    public static int[] steps(int levels) {
        int total = STEPS.size();
        int wanted = Math.clamp(levels, 1, total);
        int[] out = new int[wanted];
        if (wanted == 1) {
            out[0] = total - 1;
            return out;
        }
        for (int i = 0; i < wanted; i++) {
            out[i] = Math.round((float) i * (total - 1) / (wanted - 1));
        }
        return out;
    }

    public static List<Rung> build(int levels, String food) {
        List<Rung> rungs = new ArrayList<>();
        int level = 0;
        for (int index : steps(levels)) {
            rungs.add(new Rung(kit(++level, STEPS.get(index), food), index));
        }
        return rungs;
    }

    private static Kit kit(int level, Step step, String food) {
        ItemStack[] storage = new ItemStack[36];
        int slot = 0;
        for (String spec : step.hand().split("\\|")) {
            ItemStack item = KitItems.read(spec);
            if (item != null && slot < FOOD_SLOT) {
                storage[slot++] = item;
            }
        }
        ItemStack meal = KitItems.read(food);
        if (meal != null) {
            storage[FOOD_SLOT] = meal;
        }

        ItemStack[] armor = {
                KitItems.read(step.boots()),
                KitItems.read(step.leggings()),
                KitItems.read(step.chestplate()),
                KitItems.read(step.helmet())
        };
        return Kit.of("vanilla-" + level, storage, armor);
    }

    private static Step step(String hand) {
        return new Step(hand, "", "", "", "");
    }

    private static Step step(String hand, String helmet, String chestplate,
                             String leggings, String boots) {
        return new Step(hand, helmet, chestplate, leggings, boots);
    }
}
