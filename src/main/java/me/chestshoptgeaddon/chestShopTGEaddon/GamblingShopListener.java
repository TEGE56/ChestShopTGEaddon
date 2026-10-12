package me.chestshoptgeaddon.chestShopTGEaddon;

import com.Acrobot.ChestShop.Utils.uBlock;
import me.yic.xconomy.api.XConomyAPI;
import me.yic.xconomy.data.syncdata.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class GamblingShopListener implements Listener {

    private static final String ADMIN_PERMISSION = "chestshoptgeaddon.gamble.admin";
    private static final String USE_PERMISSION = "chestshoptgeaddon.gamble.use";
    private final ChestShopTGEaddon plugin;
    private final GamblingDisplayManager displayManager;
    private final NamespacedKey gambleShopKey;
    private final Set<String> activeGambles = new HashSet<>();

    public GamblingShopListener(ChestShopTGEaddon plugin, GamblingDisplayManager displayManager) {
        this.plugin = plugin;
        this.displayManager = displayManager;
        this.gambleShopKey = new NamespacedKey(plugin, "gamble_shop");
    }

    public void refreshLoadedSignTemplates() {
        double configuredPrice = getConfiguredPrice();
        String priceText = isValidPrice(configuredPrice)
                ? BigDecimal.valueOf(configuredPrice).stripTrailingZeros().toPlainString()
                : "";
        List<String> lines = plugin.getConfig().getStringList("gambling.sign.lines");
        for (var world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                for (var state : chunk.getTileEntities()) {
                    if (!(state instanceof Sign sign) || !isGambleSign(sign)) {
                        continue;
                    }
                    for (int line = 0; line < 4; line++) {
                        String text = line < lines.size() ? lines.get(line) : "";
                        sign.setLine(line, ChatColor.translateAlternateColorCodes(
                                '&', text.replace("%price%", priceText)));
                    }
                    sign.update();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignCreate(SignChangeEvent event) {
        if (!event.getLine(0).trim().equalsIgnoreCase("[Gamble]")) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            event.setCancelled(true);
            sendMessage(player, "no-create-permission",
                    "&cYou do not have permission to create gambling chests.");
            return;
        }

        if (!(event.getBlock().getState() instanceof Sign sign)
                || uBlock.findConnectedContainer(sign) == null) {
            event.setCancelled(true);
            sendMessage(player, "chest-required", "&cPlace the sign next to a chest.");
            return;
        }

        double price = getConfiguredPrice();
        if (!isValidPrice(price)) {
            event.setCancelled(true);
            sendMessage(player, "invalid-price-create",
                    "&cThe configured gambling price is invalid.");
            return;
        }

        String priceText = BigDecimal.valueOf(price).stripTrailingZeros().toPlainString();
        List<String> lines = plugin.getConfig().getStringList("gambling.sign.lines");
        for (int line = 0; line < 4; line++) {
            String text = line < lines.size() ? lines.get(line) : "";
            event.setLine(line, ChatColor.translateAlternateColorCodes(
                    '&', text.replace("%price%", priceText)));
        }

        Block signBlock = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (signBlock.getState() instanceof Sign placedSign) {
                placedSign.getPersistentDataContainer().set(
                        gambleShopKey, PersistentDataType.BYTE, (byte) 1);
                placedSign.update();
                displayManager.createOrRefresh(signBlock);
            }
        });

        sendMessage(player, "sign-created", "&aGambling sign created. Price: &e%price%",
                "%price%", priceText);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Sign sign)
                || !isGambleSign(sign)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission(USE_PERMISSION)) {
            sendMessage(player, "no-use-permission",
                    "&cYou do not have permission to use gambling chests.");
            return;
        }
        String gambleKey = gambleKey(clicked);
        if (activeGambles.contains(gambleKey)) {
            sendMessage(player, "gamble-in-progress",
                    "&eThis chest is already running a game. Please wait.");
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            sendMessage(player, "inventory-full", "&cYour inventory is full. Make some room first.");
            return;
        }

        double price = getConfiguredPrice();
        if (!isValidPrice(price)) {
            sendMessage(player, "invalid-price-use", "&cThe gambling price is not configured correctly.");
            return;
        }

        XConomyAPI economy = getXConomy();
        if (economy == null) {
            sendMessage(player, "economy-unavailable", "&cXConomy is not available.");
            plugin.getLogger().warning("A gambling chest was used, but the XConomy API is unavailable.");
            return;
        }

        Container chest = uBlock.findConnectedContainer(sign);
        if (chest == null) {
            sendMessage(player, "chest-not-found", "&cNo gambling chest was found next to the sign.");
            return;
        }

        List<ConfiguredReward> rewards = loadRewardTable();
        if (rewards.isEmpty()) {
            sendMessage(player, "no-rewards",
                    "&cThe gambling reward table contains no valid rewards.");
            return;
        }
        ConfiguredReward selectedReward = chooseReward(rewards);
        ItemStack reward = null;
        if (selectedReward != null) {
            int rewardAmount = selectedReward.amount;
            if (selectedReward.bonusAmount > selectedReward.amount
                    && ThreadLocalRandom.current().nextDouble(100.0)
                    < selectedReward.bonusChancePercent) {
                rewardAmount = selectedReward.bonusAmount;
            }
            reward = new ItemStack(selectedReward.material, rewardAmount);
        }

        BigDecimal cost = BigDecimal.valueOf(price);
        PlayerData playerData = economy.getPlayerData(player.getUniqueId());
        if (playerData == null) {
            playerData = economy.getPlayerData(player.getName());
        }
        if (playerData == null) {
            plugin.getLogger().warning("XConomy could not find an account for player: "
                    + player.getName() + " (" + player.getUniqueId() + ")");
            sendMessage(player, "account-not-found", "&cYour XConomy account could not be found.");
            return;
        }

        int withdrawal = economy.changePlayerBalance(
                player.getUniqueId(), player.getName(), cost, false, plugin.getName());
        if (withdrawal != 0) {
            String displayPrice = economy.getdisplay(cost);
            if (withdrawal == 2) {
                sendMessage(player, "not-enough-money",
                        "&cYou do not have enough money. Price: &e%price%",
                        "%price%", displayPrice);
            } else {
                plugin.getLogger().warning("XConomy withdrawal failed. Player="
                        + player.getName() + ", balance=" + playerData.getBalance()
                        + ", price=" + cost + ", XConomy API result=" + withdrawal);
                sendMessage(player, "payment-failed",
                        "&cPayment failed. Please try again later.");
            }
            return;
        }

        activeGambles.add(gambleKey);
        Runnable unlockGamble = () -> activeGambles.remove(gambleKey);

        if (reward == null) {
            java.util.UUID playerId = player.getUniqueId();
            Runnable notifyNoPrize = () -> {
                Player recipient = Bukkit.getPlayer(playerId);
                if (recipient != null && recipient.isOnline()) {
                    sendMessage(recipient, "no-prize",
                            "&7You paid &a%price%&7, but did not win a prize this time.",
                            "%price%", economy.getdisplay(cost));
                }
            };
            if (!displayManager.animateWin(clicked, null,
                    notifyNoPrize, unlockGamble)) {
                notifyNoPrize.run();
                unlockGamble.run();
            }
            return;
        }

        ItemStack finalReward = reward.clone();
        Runnable deliverReward = () -> deliverRewardOrRefund(
                player.getUniqueId(), player.getName(), finalReward, cost,
                economy);
        if (!displayManager.animateWin(clicked, finalReward,
                deliverReward, unlockGamble)) {
            deliverReward.run();
            unlockGamble.run();
        }
    }

    private void deliverRewardOrRefund(java.util.UUID playerId, String playerName,
                                       ItemStack reward, BigDecimal cost,
                                       XConomyAPI economy) {
        Player recipient = Bukkit.getPlayer(playerId);
        if (recipient == null || !recipient.isOnline()) {
            refundPayment(playerId, playerName, cost, economy,
                    "The player left before receiving the reward.", null);
            return;
        }

        if (!recipient.getInventory().addItem(reward).isEmpty()) {
            refundPayment(playerId, playerName, cost, economy,
                    "The player's inventory became full during the animation.", recipient);
            return;
        }

        String itemName = reward.getType().name().toLowerCase().replace('_', ' ');
        sendMessage(recipient, "prize-won",
                "&7You paid &a%price% &7and won &f%amount%x %item%&7!",
                "%price%", economy.getdisplay(cost),
                "%amount%", Integer.toString(reward.getAmount()),
                "%item%", itemName);
    }

    private void refundPayment(java.util.UUID playerId, String playerName,
                               BigDecimal cost, XConomyAPI economy,
                               String reason, Player onlinePlayer) {
        int refund = economy.changePlayerBalance(
                playerId, playerName, cost, true, plugin.getName());
        if (refund != 0) {
            plugin.getLogger().severe("Failed to refund a gambling payment to player "
                    + playerName + ". Reason=" + reason + ", XConomy API result="
                    + refund + ", amount=" + cost);
            if (onlinePlayer != null) {
                sendMessage(onlinePlayer, "refund-failed",
                        "&cThe reward could not be delivered and the payment could not be refunded. "
                                + "Please contact an administrator.");
            }
            return;
        }
        if (onlinePlayer != null) {
            sendMessage(onlinePlayer, "payment-refunded", "&aYour payment has been refunded.");
        }
    }

    private void sendMessage(Player player, String key, String defaultText,
                             String... replacements) {
        String message = plugin.getConfig().getString(
                "gambling.messages." + key, defaultText);
        if (message == null) {
            message = defaultText;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(color(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Sign sign && isGambleSign(sign)) {
            preventUnauthorizedBreak(event);
            return;
        }
        if (!(block.getState() instanceof Container)) {
            return;
        }

        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getState() instanceof Sign sign && isGambleSign(sign)) {
                preventUnauthorizedBreak(event);
                return;
            }
        }
    }

    private List<ConfiguredReward> loadRewardTable() {
        List<ConfiguredReward> rewards = new ArrayList<>();
        long totalWeightHundredths = 0;
        boolean hasExplicitNoPrizeChance = plugin.getConfig().contains(
                "gambling.rewards.no-prize-chance");
        if (hasExplicitNoPrizeChance) {
            double noPrizeChance = plugin.getConfig().getDouble(
                    "gambling.rewards.no-prize-chance");
            if (!Double.isFinite(noPrizeChance) || noPrizeChance < 0.0
                    || noPrizeChance > 100.0
                    || Math.abs(noPrizeChance * 100.0
                    - Math.round(noPrizeChance * 100.0)) > 0.000001) {
                plugin.getLogger().severe("gambling.rewards.no-prize-chance must be "
                        + "a percentage from 0.00 to 100.00.");
                return List.of();
            }
        }
        Set<String> blacklist = new HashSet<>();
        for (String materialName : plugin.getConfig().getStringList(
                "gambling.rewards.blacklist")) {
            Material blockedMaterial = Material.matchMaterial(materialName.trim());
            if (blockedMaterial == null) {
                plugin.getLogger().warning("Unknown material in gambling blacklist: "
                        + materialName);
            } else {
                blacklist.add(blockedMaterial.name());
            }
        }

        for (Map<?, ?> entry : plugin.getConfig().getMapList("gambling.rewards.items")) {
            Object materialValue = entry.get("material");
            Object amountValue = entry.get("amount");
            Object weightValue = entry.containsKey("chance")
                    ? entry.get("chance") : entry.get("weight");
            Object bonusAmountValue = entry.get("bonus-amount");
            Object bonusChanceValue = entry.get("bonus-chance");
            if (!(materialValue instanceof String materialName)
                    || !(amountValue instanceof Number amountNumber)
                    || !(weightValue instanceof Number weightNumber)) {
                plugin.getLogger().warning("Invalid gambling reward in config: " + entry);
                continue;
            }

            Material material = Material.matchMaterial(materialName.trim());
            double rawAmount = amountNumber.doubleValue();
            double rawWeight = weightNumber.doubleValue();
            int amount = amountNumber.intValue();
            long weightHundredths = Math.round(rawWeight * 100.0);
            int bonusAmount = amount;
            double bonusChancePercent = 0.0;
            if (bonusAmountValue != null || bonusChanceValue != null) {
                if (!(bonusAmountValue instanceof Number bonusAmountNumber)
                        || !(bonusChanceValue instanceof Number bonusChanceNumber)) {
                    plugin.getLogger().warning("Skipping invalid bonus reward entry: " + entry);
                    continue;
                }
                double rawBonusAmount = bonusAmountNumber.doubleValue();
                bonusAmount = bonusAmountNumber.intValue();
                bonusChancePercent = bonusChanceNumber.doubleValue();
                if (!Double.isFinite(bonusChancePercent) || bonusChancePercent < 0.0
                        || bonusChancePercent > 100.0
                        || rawBonusAmount != bonusAmount) {
                    plugin.getLogger().warning("Skipping invalid bonus reward amount/chance: "
                            + entry);
                    continue;
                }
            }

            if (material == null || material.isAir() || blacklist.contains(material.name())
                    || rawAmount != amount || !Double.isFinite(rawWeight)
                    || rawWeight < 0.0 || rawWeight > Long.MAX_VALUE / 100.0
                    || Math.abs(rawWeight * 100.0 - weightHundredths) > 0.000001
                    || amount < 1 || amount > material.getMaxStackSize()
                    || bonusAmount < amount || bonusAmount > material.getMaxStackSize()) {
                plugin.getLogger().warning("Skipping invalid gambling reward: "
                        + entry);
                continue;
            }

            if (weightHundredths == 0) {
                continue;
            }
            try {
                totalWeightHundredths = Math.addExact(
                        totalWeightHundredths, weightHundredths);
            } catch (ArithmeticException exception) {
                plugin.getLogger().severe("The total gambling reward weight is too large.");
                return List.of();
            }
            if (!hasExplicitNoPrizeChance && totalWeightHundredths > 10_000) {
                plugin.getLogger().severe("The legacy reward chances exceed 100%. Add "
                        + "gambling.rewards.no-prize-chance to use weights instead.");
                return List.of();
            }
            rewards.add(new ConfiguredReward(material, amount, weightHundredths,
                    bonusAmount, bonusChancePercent));
        }

        return rewards;
    }

    private ConfiguredReward chooseReward(List<ConfiguredReward> rewards) {
        long totalWeight = 0;
        for (ConfiguredReward reward : rewards) {
            totalWeight += reward.weightHundredths;
        }

        double noPrizeChance;
        if (plugin.getConfig().contains("gambling.rewards.no-prize-chance")) {
            noPrizeChance = plugin.getConfig().getDouble(
                    "gambling.rewards.no-prize-chance", 0.0);
        } else {
            noPrizeChance = Math.max(0.0, 100.0 - totalWeight / 100.0);
        }

        if (ThreadLocalRandom.current().nextDouble(100.0) < noPrizeChance) {
            return null;
        }

        long roll = ThreadLocalRandom.current().nextLong(totalWeight);
        long cumulativeWeight = 0;
        for (ConfiguredReward reward : rewards) {
            cumulativeWeight += reward.weightHundredths;
            if (roll < cumulativeWeight) {
                return reward;
            }
        }
        return null;
    }

    private static final class ConfiguredReward {
        private final Material material;
        private final int amount;
        private final long weightHundredths;
        private final int bonusAmount;
        private final double bonusChancePercent;

        private ConfiguredReward(Material material, int amount, long weightHundredths,
                                 int bonusAmount, double bonusChancePercent) {
            this.material = material;
            this.amount = amount;
            this.weightHundredths = weightHundredths;
            this.bonusAmount = bonusAmount;
            this.bonusChancePercent = bonusChancePercent;
        }
    }

    private void preventUnauthorizedBreak(BlockBreakEvent event) {
        if (!event.getPlayer().hasPermission(ADMIN_PERMISSION)) {
            event.setCancelled(true);
            sendMessage(event.getPlayer(), "no-destroy-permission",
                    "&cOnly an admin can destroy a gambling chest.");
        }
    }

    private boolean isGambleSign(Sign sign) {
        return sign.getPersistentDataContainer().has(gambleShopKey, PersistentDataType.BYTE);
    }

    private String gambleKey(Block signBlock) {
        return signBlock.getWorld().getUID() + ":"
                + signBlock.getX() + ":"
                + signBlock.getY() + ":"
                + signBlock.getZ();
    }

    private boolean isValidPrice(double price) {
        return Double.isFinite(price) && price > 0;
    }

    private double getConfiguredPrice() {
        return plugin.getConfig().getDouble("gambling.price", 100.0);
    }

    private XConomyAPI getXConomy() {
        if (Bukkit.getPluginManager().getPlugin("XConomy") == null
                || !Bukkit.getPluginManager().isPluginEnabled("XConomy")) {
            return null;
        }
        return new XConomyAPI();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
