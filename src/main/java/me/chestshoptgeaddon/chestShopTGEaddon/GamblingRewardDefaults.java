package me.chestshoptgeaddon.chestShopTGEaddon;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class GamblingRewardDefaults {

    private static final String REWARDS_PATH = "gambling.rewards.items";
    private static final String GENERATE_PATH = "gambling.rewards.generate-all-survival-items";

    private GamblingRewardDefaults() {
    }

    public static int generateIfRequested(JavaPlugin plugin) {
        boolean requested = plugin.getConfig().getBoolean(GENERATE_PATH, false);
        if (!requested) {
            if (plugin.getConfig().getMapList(REWARDS_PATH).isEmpty()) {
                plugin.getLogger().warning(
                        "The gambling reward list is empty and automatic generation is disabled. "
                                + "Add rewards to gambling.rewards.items or set "
                                + GENERATE_PATH + " to true.");
            }
            return -1;
        }

        Set<String> excluded = new HashSet<>();
        for (String materialName : plugin.getConfig().getStringList(
                "gambling.rewards.blacklist")) {
            Material material = Material.matchMaterial(materialName.trim());
            if (material == null) {
                plugin.getLogger().warning("Unknown material in gambling.rewards.blacklist: "
                        + materialName);
            } else {
                excluded.add(material.name());
            }
        }

        List<GeneratedReward> generatedRewards = new ArrayList<>();
        int itemMaterialsSeen = 0;
        for (Material material : Material.values()) {
            String name = material.name();
            if (material.isItem()) {
                itemMaterialsSeen++;
            }
            if (!material.isItem() || material.isLegacy() || material.isAir()
                    || excluded.contains(name) || isTechnicalOrUnobtainable(name)) {
                continue;
            }

            int maxStack = material.getMaxStackSize();
            if (maxStack < 1) {
                continue;
            }

            int amount = suggestedAmount(material, maxStack);
            generatedRewards.add(new GeneratedReward(material, amount,
                    suggestedWeight(material), isHighValue(name) && maxStack > 1));
        }

        if (generatedRewards.isEmpty()) {
            plugin.getLogger().severe("Could not generate the reward list. Paper Material values: "
                    + Material.values().length + ", recognized as item materials: "
                    + itemMaterialsSeen + ", blacklisted: " + excluded.size()
                    + ". Check the blacklist and make sure the server is running Paper 26.3.");
            return 0;
        }

        if (generatedRewards.size() > 10_000) {
            plugin.getLogger().severe("There are more than 10,000 rewards, so each cannot be "
                    + "assigned a chance of at least 0.01%.");
            return 0;
        }

        long totalRelativeChance = generatedRewards.stream()
                .mapToLong(reward -> reward.relativeWeight).sum();
        int remainingHundredths = 10_000 - generatedRewards.size();
        int allocatedHundredths = 0;
        for (GeneratedReward reward : generatedRewards) {
            double exactShare = remainingHundredths
                    * (double) reward.relativeWeight / totalRelativeChance;
            int wholeShare = (int) Math.floor(exactShare);
            reward.chanceHundredths = 1 + wholeShare;
            reward.fractionalShare = exactShare - wholeShare;
            allocatedHundredths += wholeShare;
        }
        int leftoverHundredths = remainingHundredths - allocatedHundredths;
        generatedRewards.stream()
                .sorted(Comparator.comparingDouble(
                        (GeneratedReward reward) -> reward.fractionalShare).reversed())
                .limit(leftoverHundredths)
                .forEach(reward -> reward.chanceHundredths++);

        List<Map<String, Object>> entries = new ArrayList<>();
        for (GeneratedReward reward : generatedRewards) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("material", reward.material.name());
            entry.put("amount", reward.amount);
            entry.put("chance", reward.chanceHundredths / 100.0);
            if (reward.hasBonusAmount) {
                entry.put("bonus-amount", 2);
                entry.put("bonus-chance", 2.0);
            }
            entries.add(entry);
        }

        plugin.getConfig().set(REWARDS_PATH, entries);
        plugin.getConfig().set(GENERATE_PATH, false);
        plugin.saveConfig();
        plugin.getLogger().info("Generated " + entries.size()
                + " default rewards in config.yml (Paper detected "
                + itemMaterialsSeen + " item materials). The list can now be edited.");
        return entries.size();
    }

    private static boolean isTechnicalOrUnobtainable(String name) {
        return name.startsWith("LEGACY_")
                || name.endsWith("_SPAWN_EGG");
    }

    private static int suggestedAmount(Material material, int maxStack) {
        String name = material.name();
        if (name.endsWith("_SMITHING_TEMPLATE") || name.contains("_ARMOR_TRIM_")) {
            return 1;
        }
        if (isHighValue(name) || maxStack == 1) {
            return 1;
        }
        if (name.equals("TORCH") || name.equals("COAL") || name.equals("ARROW")
                || name.equals("STICK")) {
            return Math.min(16, maxStack);
        }
        if (material.isEdible()) {
            return Math.min(8, maxStack);
        }
        if (material.isBlock()) {
            return Math.min(16, maxStack);
        }
        return Math.min(4, maxStack);
    }

    private static int suggestedWeight(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        if (name.equals("TORCH")) return 500;
        if (name.equals("BREAD")) return 350;
        if (name.equals("COAL")) return 300;
        if (name.equals("ARROW") || name.equals("STICK")) return 180;
        if (name.equals("COBBLESTONE") || name.equals("OAK_PLANKS")
                || name.equals("DIRT")) return 120;
        if (isHighValue(name)) return 1;
        if (material.isBlock()) return 8;
        if (material.isEdible()) return 30;
        if (material.getMaxStackSize() == 1) return 3;
        return 15;
    }

    private static boolean isHighValue(String name) {
        return name.contains("DIAMOND") || name.contains("EMERALD")
                || name.startsWith("NETHERITE_") || name.equals("TRIDENT")
                || name.equals("MACE") || name.equals("TOTEM_OF_UNDYING")
                || name.equals("ENCHANTED_GOLDEN_APPLE")
                || name.contains("ANCIENT_DEBRIS")
                || name.contains("HEART_OF_THE_SEA") || name.contains("NAUTILUS_SHELL")
                || name.contains("SHULKER_SHELL") || name.contains("WITHER_SKELETON_SKULL")
                || name.contains("GOLD_INGOT") || name.contains("RAW_GOLD")
                || name.contains("GOLD_BLOCK") || name.contains("GOLD_ORE");
    }

    private static final class GeneratedReward {
        private final Material material;
        private final int amount;
        private final int relativeWeight;
        private final boolean hasBonusAmount;
        private int chanceHundredths;
        private double fractionalShare;
        private GeneratedReward(Material material, int amount, int relativeWeight,
                                boolean hasBonusAmount) {
            this.material = material;
            this.amount = amount;
            this.relativeWeight = relativeWeight;
            this.hasBonusAmount = hasBonusAmount;
        }
    }
}
