package me.chestshoptgeaddon.chestShopTGEaddon;

import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.Chunk;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class GamblingDisplayManager implements Listener {

    private final ChestShopTGEaddon plugin;
    private final NamespacedKey gambleShopKey;
    private final Map<String, ManagedDisplay> displays = new HashMap<>();
    private List<ItemStack> previewItems;

    public GamblingDisplayManager(ChestShopTGEaddon plugin) {
        this.plugin = plugin;
        this.gambleShopKey = new NamespacedKey(plugin, "gamble_shop");
        this.previewItems = loadPreviewItems();
    }

    public void restoreLoadedDisplays() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (var chunk : world.getLoadedChunks()) {
                    restoreDisplaysIn(chunk.getTileEntities());
                }
            }
        });
    }

    public void reloadSettings() {
        previewItems = loadPreviewItems();
        for (ManagedDisplay display : List.copyOf(displays.values())) {
            if (!display.isHealthy()) {
                continue;
            }
            Container chest = display.signBlock.getState() instanceof Sign sign
                    ? uBlock.findConnectedContainer(sign) : null;
            if (chest != null) {
                display.base = displayBase(chest.getLocation());
            }
            if (display.isAnimating()) {
                continue;
            }
            if (previewItems.isEmpty()) {
                removeDisplay(display.signBlock);
                continue;
            }
            ItemStack item = choosePreviewItem(display.itemDisplay.getItemStack());
            if (item != null) {
                display.updateItem(item);
                display.nextItemChangeTick = display.ticks + getDisplayChangeIntervalTicks();
            }
        }
    }

    private String getSound(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    private void playSound(Location loc, String soundKey, String volumePath) {
        if (soundKey == null || soundKey.isEmpty()) return;
        World world = loc.getWorld();
        if (world == null) return;

        float volume = (float) plugin.getConfig().getDouble(volumePath, 1.0);
        world.playSound(loc, soundKey, volume, 1.0f);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> restoreDisplaysIn(chunk.getTileEntities()), 1L);
    }

    private void restoreDisplaysIn(org.bukkit.block.BlockState[] tileEntities) {
        for (org.bukkit.block.BlockState state : tileEntities) {
            if (state instanceof Sign sign && isGambleSign(sign)) {
                createOrRefresh(sign.getBlock());
            }
        }
    }

    public void createOrRefresh(Block signBlock) {
        if (!(signBlock.getState() instanceof Sign sign) || !isGambleSign(sign)) {
            return;
        }

        Container chest = uBlock.findConnectedContainer(sign);
        if (chest == null) {
            return;
        }

        ItemStack displayItem = choosePreviewItem(null);
        if (displayItem == null) {
            plugin.getLogger().warning("No valid rewards were found for the gambling display in config.yml.");
            return;
        }

        String tag = displayTag(signBlock);
        ManagedDisplay existing = displays.get(tag);
        if (existing != null && existing.isHealthy()) {
            if (!existing.isAnimating()) {
                existing.updateItem(displayItem);
            }
            return;
        }

        ItemDisplay itemDisplay = null;
        for (Entity entity : chest.getBlock().getChunk().getEntities()) {
            if (entity instanceof ItemDisplay candidate
                    && candidate.getScoreboardTags().contains(tag)) {
                if (itemDisplay == null) {
                    itemDisplay = candidate;
                } else {
                    candidate.remove();
                }
            }
        }

        Location chestLocation = chest.getLocation().clone();
        Location base = displayBase(chestLocation);
        if (itemDisplay == null || !itemDisplay.isValid()) {
            itemDisplay = chest.getWorld().spawn(base, ItemDisplay.class);
        }
        itemDisplay.setItemStack(displayItem);
        itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        itemDisplay.setBillboard(Display.Billboard.FIXED);
        itemDisplay.setTeleportDuration(2);
        itemDisplay.setPersistent(true);
        itemDisplay.addScoreboardTag(tag);

        ManagedDisplay managed = new ManagedDisplay(
                signBlock, itemDisplay, base);
        displays.put(tag, managed);
        managed.start(tag);
    }

    public boolean animateWin(Block signBlock, ItemStack winningItem,
                              Runnable onWinShown, Runnable onAnimationComplete) {
        createOrRefresh(signBlock);
        ManagedDisplay managed = displays.get(displayTag(signBlock));
        if (managed == null || !managed.isHealthy() || managed.isAnimating()) {
            return false;
        }

        double durationSeconds = plugin.getConfig().getDouble(
                "gambling.animation.duration-seconds", 3.0);
        double resultSeconds = plugin.getConfig().getDouble(
                "gambling.animation.result-display-seconds", 1.5);
        long durationTicks = secondsToTicks(durationSeconds, 3.0);
        long resultTicks = secondsToTicks(resultSeconds, 1.5);
        managed.beginAnimation(winningItem, durationTicks, resultTicks,
                onWinShown, onAnimationComplete);
        return true;
    }

    private long secondsToTicks(double seconds, double fallbackSeconds) {
        double safeSeconds = Double.isFinite(seconds) ? Math.max(0.5, seconds) : fallbackSeconds;
        return Math.max(10L, Math.round(safeSeconds * 20.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        if (broken.getState() instanceof Sign sign && isGambleSign(sign)) {
            removeDisplay(broken);
            return;
        }

        if (!(broken.getState() instanceof Container)) {
            return;
        }
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
            Block nearby = broken.getRelative(face);
            if (nearby.getState() instanceof Sign sign && isGambleSign(sign)) {
                removeDisplay(nearby);
            }
        }
    }

    public void shutdown() {
        for (ManagedDisplay display : displays.values()) {
            display.finishAnimation();
            display.stop();
        }
        displays.clear();
    }

    private void removeDisplay(Block signBlock) {
        String tag = displayTag(signBlock);
        ManagedDisplay managed = displays.remove(tag);
        if (managed != null) {
            managed.finishAnimation();
            managed.stop();
        }

        for (Entity entity : signBlock.getWorld().getEntities()) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private List<ItemStack> loadPreviewItems() {
        List<ItemStack> items = new ArrayList<>();
        Set<String> blacklist = new HashSet<>();
        for (String materialName : plugin.getConfig().getStringList(
                "gambling.rewards.blacklist")) {
            Material blockedMaterial = Material.matchMaterial(materialName.trim());
            if (blockedMaterial != null) {
                blacklist.add(blockedMaterial.name());
            }
        }
        for (Map<?, ?> entry : plugin.getConfig().getMapList("gambling.rewards.items")) {
            Object materialValue = entry.get("material");
            Object amountValue = entry.get("amount");
            Object weightValue = entry.containsKey("chance")
                    ? entry.get("chance") : entry.get("weight");
            if (!(materialValue instanceof String materialName)
                    || !(amountValue instanceof Number amountNumber)
                    || !(weightValue instanceof Number weightNumber)) {
                continue;
            }

            Material material = Material.matchMaterial(materialName.trim());
            int amount = amountNumber.intValue();
            double weight = weightNumber.doubleValue();
            if (material == null || material.isAir() || blacklist.contains(material.name())
                    || amountNumber.doubleValue() != amount
                    || !Double.isFinite(weight) || weight <= 0.0
                    || weight > Long.MAX_VALUE / 100.0
                    || Math.abs(weight * 100.0 - Math.round(weight * 100.0)) > 0.000001
                    || amount < 1 || amount > material.getMaxStackSize()) {
                continue;
            }
            items.add(new ItemStack(material, amount));
        }
        return List.copyOf(items);
    }

    private ItemStack choosePreviewItem(ItemStack current) {
        if (previewItems.isEmpty()) {
            return null;
        }

        List<ItemStack> candidates = previewItems;
        if (current != null && previewItems.size() > 1) {
            List<ItemStack> differentItems = previewItems.stream()
                    .filter(item -> !item.isSimilar(current))
                    .toList();
            if (!differentItems.isEmpty()) {
                candidates = differentItems;
            }
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).clone();
    }

    private long getDisplayChangeIntervalTicks() {
        int seconds = Math.max(1, plugin.getConfig().getInt("gambling.display.change-seconds", 1));
        return seconds * 20L;
    }

    private Location displayBase(Location chestLocation) {
        double height = plugin.getConfig().getDouble("gambling.display.height", 1.15);
        return chestLocation.clone().add(0.5, height, 0.5);
    }

    private boolean isGambleSign(Sign sign) {
        return sign.getPersistentDataContainer().has(gambleShopKey, PersistentDataType.BYTE);
    }

    private String displayTag(Block signBlock) {
        return "cstgea-gamble-" + signBlock.getWorld().getUID().toString().replace("-", "")
                + "-" + signBlock.getX() + "-" + signBlock.getY() + "-" + signBlock.getZ();
    }

    private final class ManagedDisplay {
        private final Block signBlock;
        private final ItemDisplay itemDisplay;
        private Location base;
        private BukkitTask task;
        private long ticks;
        private long nextItemChangeTick;
        private long nextAnimationItemTick;
        private WinAnimation animation;

        private ManagedDisplay(Block signBlock, ItemDisplay itemDisplay, Location base) {
            this.signBlock = signBlock;
            this.itemDisplay = itemDisplay;
            this.base = base.clone();
            this.nextItemChangeTick = getDisplayChangeIntervalTicks();
        }

        private void updateItem(ItemStack item) {
            itemDisplay.setItemStack(item == null
                    ? new ItemStack(Material.AIR) : item);
        }

        private boolean isAnimating() {
            return animation != null;
        }

        private void beginAnimation(ItemStack winningItem, long durationTicks,
                                    long resultHoldTicks, Runnable onWinShown,
                                    Runnable onAnimationComplete) {

            String spinSound = getSound("gambling.sounds.spin-start", "minecraft:block.note_block.hat");
            playSound(base, spinSound, "gambling.sounds.spin-start-volume");

            animation = new WinAnimation(
                    winningItem == null ? null : winningItem.clone(),
                    ticks,
                    durationTicks,
                    resultHoldTicks,
                    onWinShown,
                    onAnimationComplete
            );

            nextAnimationItemTick = ticks;
        }

        private void finishAnimation() {
            WinAnimation current = animation;
            if (current == null) {
                return;
            }

            if (current.winningItem == null) {
                String noPrizeSound = getSound("gambling.sounds.no-prize", "minecraft:block.anvil.land");
                playSound(base, noPrizeSound, "gambling.sounds.no-prize-volume");
            }

            animation = null;
            updateItem(current.winningItem);

            if (!current.winShown) {
                current.winShown = true;

                String winSound = getSound("gambling.sounds.win", "minecraft:entity.player.levelup");
                playSound(base, winSound, "gambling.sounds.win-volume");

                runCallback(current.onWinShown, "showing the prize");
            }
            runCallback(current.onAnimationComplete, "completing the animation");
        }

        private void runCallback(Runnable callback, String description) {
            if (callback == null) {
                return;
            }
            try {
                callback.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Gambling animation " + description
                        + " failed: " + exception.getMessage());
            }
        }

        private void start(String tag) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!itemDisplay.isValid()) {
                    finishAnimation();
                    stop();
                    displays.remove(tag, this);
                    return;
                }
                if (!(signBlock.getState() instanceof Sign sign) || !isGambleSign(sign)) {
                    finishAnimation();
                    stop();
                    itemDisplay.remove();
                    displays.remove(tag, this);
                    return;
                }

                ticks += 2;
                WinAnimation currentAnimation = animation;
                if (currentAnimation != null) {
                    long elapsed = ticks - currentAnimation.startTick;
                    if (elapsed >= currentAnimation.durationTicks) {
                        updateItem(currentAnimation.winningItem);

                        if (!currentAnimation.winShown) {

                            if (currentAnimation.winningItem == null) {
                                String noPrizeSound = getSound("gambling.sounds.no-prize", "minecraft:block.anvil.land");
                                playSound(base, noPrizeSound, "gambling.sounds.no-prize-volume");
                            } else {
                                String winSound = getSound("gambling.sounds.win", "minecraft:entity.player.levelup");
                                playSound(base, winSound, "gambling.sounds.win-volume");
                            }

                            currentAnimation.winShown = true;
                            runCallback(currentAnimation.onWinShown, "showing the prize");
                        }

                        if (elapsed >= currentAnimation.durationTicks + currentAnimation.resultHoldTicks) {
                            animation = null;
                            runCallback(currentAnimation.onAnimationComplete, "completing the animation");

                            if (previewItems.isEmpty()) {
                                itemDisplay.remove();
                                stop();
                                displays.remove(tag, this);
                                return;
                            }
                            nextItemChangeTick = ticks + getDisplayChangeIntervalTicks();
                        }

                    } else if (ticks >= nextAnimationItemTick) {
                        ItemStack nextItem = choosePreviewItem(itemDisplay.getItemStack());
                        if (nextItem != null) {
                            updateItem(nextItem);

                            String spinTickSound = getSound("gambling.sounds.spin-tick", "");
                            playSound(base, spinTickSound, "gambling.sounds.spin-tick-volume");
                        }

                        double progress = elapsed / (double) currentAnimation.durationTicks;
                        long changeInterval = progress < 0.65 ? 2L
                                : progress < 0.85 ? 4L : 8L;
                        nextAnimationItemTick = ticks + changeInterval;
                    }

                } else if (ticks >= nextItemChangeTick) {
                    ItemStack nextItem = choosePreviewItem(itemDisplay.getItemStack());
                    if (nextItem != null) {
                        updateItem(nextItem);
                    }
                    nextItemChangeTick = ticks + getDisplayChangeIntervalTicks();
                }
                double bobHeight = Math.max(0.0,
                        plugin.getConfig().getDouble("gambling.display.bob-height", 0.03));
                double bob = Math.sin(ticks * 0.08) * bobHeight;
                Location next = base.clone().add(0, bob, 0);
                next.setYaw((ticks * 3.0f) % 360.0f);
                next.setPitch(0);
                itemDisplay.teleport(next);
            }, 0L, 2L);
        }

        private boolean isHealthy() {
            return task != null && !task.isCancelled() && itemDisplay.isValid();
        }

        private void stop() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private static final class WinAnimation {
        private final ItemStack winningItem;
        private final long startTick;
        private final long durationTicks;
        private final long resultHoldTicks;
        private final Runnable onWinShown;
        private final Runnable onAnimationComplete;
        private boolean winShown;

        private WinAnimation(ItemStack winningItem, long startTick, long durationTicks,
                             long resultHoldTicks, Runnable onWinShown,
                             Runnable onAnimationComplete) {
            this.winningItem = winningItem;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.resultHoldTicks = resultHoldTicks;
            this.onWinShown = onWinShown;
            this.onAnimationComplete = onAnimationComplete;
        }
    }
}
