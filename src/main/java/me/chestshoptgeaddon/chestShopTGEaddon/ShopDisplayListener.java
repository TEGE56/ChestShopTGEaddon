package me.chestshoptgeaddon.chestShopTGEaddon;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Events.ShopDestroyedEvent;
import com.Acrobot.ChestShop.Events.ItemParseEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.Utils.uBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShopDisplayListener implements Listener {

    private static final int SIGN_LOOK_DISTANCE = 3;
    private static final long TEXT_LINGER_TICKS = 60L;
    private static final double SIGN_FORMAT_DISTANCE_SQUARED = 1024.0;
    private static final Pattern SIGN_COORDINATES =
            Pattern.compile("^(-?\\d+)-(-?\\d+)-(-?\\d+)$");

    private final JavaPlugin plugin;
    private final Map<String, ShopDisplay> displays = new HashMap<>();
    private final File registryFile;
    private final Set<String> registeredShops = new LinkedHashSet<>();
    private final Map<String, String> shopContainerChunks = new HashMap<>();
    private final Set<String> desiredShopChunkTickets = new HashSet<>();
    private final Set<String> ticketedShopChunks = new HashSet<>();
    private final Set<String> pendingShopChunkLoads = new HashSet<>();
    private boolean shuttingDown;

    private record ShopPrices(String buyPrice, String sellPrice) {
        private boolean isDualPrice() {
            return buyPrice != null && sellPrice != null;
        }
    }

    public ShopDisplayListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registryFile = new File(plugin.getDataFolder(), "shops.yml");
        loadShopRegistry();
    }

    public void restoreLoadedDisplays() {
        reconcileShopChunkTickets();
        Bukkit.getScheduler().runTask(plugin, this::restoreAllLoadedDisplays);
        Bukkit.getScheduler().runTaskLater(plugin, this::restoreAllLoadedDisplays, 20L);
    }

    public void refreshFormattedSigns() {
        for (ShopDisplay display : List.copyOf(displays.values())) {
            if (!display.isHealthy()) {
                continue;
            }
            for (UUID viewerId : Set.copyOf(display.formattedSignViewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    sendFormattedSign(viewer, display.signBlock);
                }
            }
        }
    }

    public void shutdown() {
        shuttingDown = true;
        for (String key : Set.copyOf(ticketedShopChunks)) {
            removeShopChunkTicket(key);
        }
        ticketedShopChunks.clear();
        desiredShopChunkTickets.clear();
        pendingShopChunkLoads.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk loadedChunk = event.getChunk();
        scheduleRestoreDisplaysAround(loadedChunk);
    }

    @EventHandler
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(chunk.getWorld())
                    || !chunk.isLoaded()) {
                return;
            }
            restoreDisplaysIn(chunk);
            restoreRegisteredShopChunksAround(chunk);
            scheduleChunkSignUpdate(player, chunk, 45L);
        }, 1L);
    }

    private void restoreRegisteredShopChunksAround(Chunk loadedChunk) {
        World world = loadedChunk.getWorld();
        UUID worldId = world.getUID();
        Set<Long> visitedChunks = new HashSet<>();

        for (String key : List.copyOf(registeredShops)) {
            String[] parts = key.split(";", -1);
            if (parts.length != 4 || !parts[0].equals(worldId.toString())) continue;
            try {
                int blockX = Integer.parseInt(parts[1]);
                int blockZ = Integer.parseInt(parts[3]);
                int shopChunkX = blockX >> 4;
                int shopChunkZ = blockZ >> 4;
                if (Math.abs(shopChunkX - loadedChunk.getX()) > 1
                        || Math.abs(shopChunkZ - loadedChunk.getZ()) > 1
                        || !world.isChunkLoaded(shopChunkX, shopChunkZ)) {
                    continue;
                }
                long chunkKey = ((long) shopChunkX << 32) ^ (shopChunkZ & 0xffffffffL);
                if (visitedChunks.add(chunkKey)) {
                    restoreRegisteredShopsIn(world.getChunkAt(shopChunkX, shopChunkZ));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void scheduleChunkSignUpdate(Player player, Chunk chunk, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.getWorld().equals(chunk.getWorld())
                    || !chunk.isLoaded()) {
                return;
            }
            for (org.bukkit.block.BlockState blockState : chunk.getTileEntities()) {
                if (!(blockState instanceof Sign sign) || !ChestShopSign.isValid(sign)) {
                    continue;
                }
                Block signBlock = sign.getBlock();
                if (player.getLocation().distanceSquared(
                        signBlock.getLocation().clone().add(0.5, 0.5, 0.5)
                ) > SIGN_FORMAT_DISTANCE_SQUARED) {
                    continue;
                }
                ShopDisplay display = displays.get(displayTag(signBlock));
                if (display != null) {
                    display.formattedSignViewers.add(player.getUniqueId());
                    display.lastSignUpdateTick.put(player.getUniqueId(), display.ticks);
                }
                sendFormattedSign(player, signBlock);
            }
        }, delay);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        for (ShopDisplay display : displays.values()) {
            display.formattedSignViewers.remove(playerId);
            display.lastSignUpdateTick.remove(playerId);
            display.lastLookTick.remove(playerId);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            restoreDisplaysAround(player.getChunk());
            refreshDisplaysForPlayer(player);
        }, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld())
                || ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4))) {
            return;
        }
        schedulePlayerDisplayRefresh(event.getPlayer(), 5L);
    }

    private void schedulePlayerDisplayRefresh(Player player, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshDisplaysForPlayer(player);
            }
        }, delay);
    }

    private void refreshDisplaysForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        for (ShopDisplay display : displays.values()) {
            if (!display.signBlock.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(
                    display.signBlock.getLocation().clone().add(0.5, 0.5, 0.5)
            );
            if (distance <= SIGN_FORMAT_DISTANCE_SQUARED
                    && display.formattedSignViewers.add(playerId)) {
                display.lastSignUpdateTick.put(playerId, display.ticks);
                sendFormattedSign(player, display.signBlock);
            }
        }
    }

    private void restoreDisplaysAround(Chunk loadedChunk) {
        World world = loadedChunk.getWorld();
        for (int x = loadedChunk.getX() - 1; x <= loadedChunk.getX() + 1; x++) {
            for (int z = loadedChunk.getZ() - 1; z <= loadedChunk.getZ() + 1; z++) {
                if (world.isChunkLoaded(x, z)) {
                    restoreDisplaysIn(world.getChunkAt(x, z));
                }
            }
        }
        restoreRegisteredShopsForContainerChunk(loadedChunk);
    }

    private void restoreRegisteredShopsForContainerChunk(Chunk containerChunk) {
        World world = containerChunk.getWorld();
        String loadedChunkKey = shopChunkKey(
                world.getUID(), containerChunk.getX(), containerChunk.getZ()
        );
        for (Map.Entry<String, String> entry : Map.copyOf(shopContainerChunks).entrySet()) {
            if (!loadedChunkKey.equals(entry.getValue())) continue;
            String[] parts = entry.getKey().split(";", -1);
            if (parts.length != 4 || !parts[0].equals(world.getUID().toString())) continue;
            try {
                int signX = Integer.parseInt(parts[1]);
                int signZ = Integer.parseInt(parts[3]);
                int signChunkX = signX >> 4;
                int signChunkZ = signZ >> 4;
                if (world.isChunkLoaded(signChunkX, signChunkZ)) {
                    restoreRegisteredShopsIn(world.getChunkAt(signChunkX, signChunkZ));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void restoreAllLoadedDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                restoreDisplaysIn(chunk);
            }
        }
    }

    private void scheduleRestoreDisplaysAround(Chunk loadedChunk) {
        World world = loadedChunk.getWorld();
        int chunkX = loadedChunk.getX();
        int chunkZ = loadedChunk.getZ();
        for (long delay : new long[]{1L, 10L, 40L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    restoreDisplaysAround(world.getChunkAt(chunkX, chunkZ));
                }
            }, delay);
        }
    }

    private int restoreDisplaysIn(Chunk chunk) {
        int restored = 0;
        boolean registryChanged = false;
        Set<String> tags = new HashSet<>();
        String tagPrefix = displayTagPrefix(chunk.getWorld());

        for (Entity entity : chunk.getEntities()) {
            for (String tag : entity.getScoreboardTags()) {
                if (tag.startsWith(tagPrefix)) {
                    tags.add(tag);
                }
            }
        }

        for (org.bukkit.block.BlockState blockState : chunk.getTileEntities()) {
            if (!(blockState instanceof Sign sign)
                    || !ChestShopSign.isValid(sign)) {
                continue;
            }

            Block signBlock = sign.getBlock();
            Container container = uBlock.findConnectedContainer(sign);
            if (container == null) {
                registryChanged |= registeredShops.add(registryKey(signBlock));
                continue;
            }
            registryChanged |= rememberShop(signBlock, container);
            String tag = displayTag(signBlock);
            ShopDisplay existingDisplay = displays.get(tag);
            if (existingDisplay != null && existingDisplay.isHealthy()) {
                continue;
            }

            removeDisplays(signBlock);
            String itemCode = ChestShopSign.getItem(sign);
            ItemStack item = parseShopItem(itemCode);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            createDisplay(signBlock, container, item);
            restored++;
        }
        if (registryChanged) {
            saveShopRegistry();
            reconcileShopChunkTickets();
        }

        for (String tag : tags) {
            Block signBlock = signBlockFromTag(chunk.getWorld(), tag);
            if (signBlock == null) {
                removeTaggedEntities(chunk.getWorld(), tag);
                continue;
            }
            World world = signBlock.getWorld();
            if (!world.isChunkLoaded(signBlock.getX() >> 4, signBlock.getZ() >> 4)) {
                continue;
            }
            if (!ChestShopSign.isValid(signBlock)) {
                continue;
            } else if (uBlock.findConnectedContainer(signBlock) == null) {
                continue;
            }
        }
        restoreRegisteredShopsIn(chunk);
        return restored;
    }

    private void removeTaggedEntities(World world, String tag) {
        ShopDisplay display = displays.remove(tag);
        if (display != null) {
            display.stop();
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private void loadShopRegistry() {
        if (!registryFile.isFile()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(registryFile);
        registeredShops.addAll(data.getStringList("shops"));
        if (data.getConfigurationSection("container-chunks") != null) {
            for (String shopKey : data.getConfigurationSection("container-chunks").getKeys(false)) {
                String chunkKey = data.getString("container-chunks." + shopKey);
                if (chunkKey != null) shopContainerChunks.put(shopKey, chunkKey);
            }
        }
    }

    private void saveShopRegistry() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("shops", List.copyOf(registeredShops));
        for (Map.Entry<String, String> entry : shopContainerChunks.entrySet()) {
            data.set("container-chunks." + entry.getKey(), entry.getValue());
        }
        try {
            File parent = registryFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the shop registry directory.");
                return;
            }
            data.save(registryFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save the shop registry.");
        }
    }

    private String registryKey(Block signBlock) {
        return signBlock.getWorld().getUID() + ";"
                + signBlock.getX() + ";" + signBlock.getY() + ";" + signBlock.getZ();
    }

    private boolean rememberShop(Block signBlock, Container container) {
        String shopKey = registryKey(signBlock);
        boolean changed = registeredShops.add(shopKey);
        if (container != null) {
            Block chestBlock = container.getBlock();
            String chunkKey = shopChunkKey(
                    chestBlock.getWorld().getUID(),
                    chestBlock.getX() >> 4,
                    chestBlock.getZ() >> 4
            );
            if (!chunkKey.equals(shopContainerChunks.put(shopKey, chunkKey))) {
                changed = true;
            }
        }
        return changed;
    }

    private void registerShop(Block signBlock, Container container) {
        if (rememberShop(signBlock, container)) {
            saveShopRegistry();
            reconcileShopChunkTickets();
        }
    }

    private void unregisterShop(Block signBlock) {
        String shopKey = registryKey(signBlock);
        boolean removed = registeredShops.remove(shopKey);
        removed |= shopContainerChunks.remove(shopKey) != null;
        if (removed) {
            saveShopRegistry();
            reconcileShopChunkTickets();
        }
    }

    private void reconcileShopChunkTickets() {
        if (shuttingDown || !plugin.isEnabled()) return;

        Set<String> desired = new LinkedHashSet<>();
        for (String shopKey : registeredShops) {
            String[] parts = shopKey.split(";", -1);
            if (parts.length != 4) continue;
            try {
                UUID worldId = UUID.fromString(parts[0]);
                int blockX = Integer.parseInt(parts[1]);
                int blockZ = Integer.parseInt(parts[3]);
                int chunkX = blockX >> 4;
                int chunkZ = blockZ >> 4;

                Set<Integer> xs = new HashSet<>();
                Set<Integer> zs = new HashSet<>();
                xs.add(chunkX);
                zs.add(chunkZ);
                int localX = Math.floorMod(blockX, 16);
                int localZ = Math.floorMod(blockZ, 16);
                if (localX == 0) xs.add(chunkX - 1);
                if (localX == 15) xs.add(chunkX + 1);
                if (localZ == 0) zs.add(chunkZ - 1);
                if (localZ == 15) zs.add(chunkZ + 1);
                for (int x : xs) {
                    for (int z : zs) {
                        desired.add(shopChunkKey(worldId, x, z));
                    }
                }
                String containerChunkKey = shopContainerChunks.get(shopKey);
                if (containerChunkKey != null) {
                    desired.add(containerChunkKey);
                } else {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            desired.add(shopChunkKey(worldId, chunkX + dx, chunkZ + dz));
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        desiredShopChunkTickets.clear();
        desiredShopChunkTickets.addAll(desired);

        for (String key : Set.copyOf(ticketedShopChunks)) {
            if (!desired.contains(key)) removeShopChunkTicket(key);
        }
        for (String key : desired) {
            if (!ticketedShopChunks.contains(key) && !pendingShopChunkLoads.contains(key)) {
                loadAndPinShopChunk(key);
            }
        }
    }

    private String shopChunkKey(UUID worldId, int x, int z) {
        return worldId + ";" + x + ";" + z;
    }

    private void loadAndPinShopChunk(String key) {
        String[] parts = key.split(";", -1);
        if (parts.length != 3) return;
        World world;
        int x;
        int z;
        try {
            world = Bukkit.getWorld(UUID.fromString(parts[0]));
            x = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (world == null) return;

        pendingShopChunkLoads.add(key);
        if (world.isChunkLoaded(x, z)) {
            pinShopChunk(key, world.getChunkAt(x, z));
            return;
        }
        world.getChunkAtAsync(x, z, false).whenComplete((chunk, error) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingShopChunkLoads.remove(key);
                    if (shuttingDown || !plugin.isEnabled()
                            || !desiredShopChunkTickets.contains(key)
                            || error != null || chunk == null) {
                        return;
                    }
                    pinShopChunk(key, chunk);
                });
            } catch (IllegalPluginAccessException ignored) {
            }
        });
    }

    private void pinShopChunk(String key, Chunk chunk) {
        pendingShopChunkLoads.remove(key);
        chunk.addPluginChunkTicket(plugin);
        ticketedShopChunks.add(key);
        restoreDisplaysAround(chunk);
    }

    private void removeShopChunkTicket(String key) {
        String[] parts = key.split(";", -1);
        if (parts.length == 3) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(parts[0]));
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                if (world != null && world.isChunkLoaded(x, z)) {
                    world.removePluginChunkTicket(x, z, plugin);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        ticketedShopChunks.remove(key);
    }

    private void restoreRegisteredShopsIn(Chunk chunk) {
        World world = chunk.getWorld();
        String prefix = world.getUID() + ";";
        for (String key : List.copyOf(registeredShops)) {
            if (!key.startsWith(prefix)) continue;
            String[] parts = key.split(";", -1);
            if (parts.length != 4) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                if ((x >> 4) != chunk.getX() || (z >> 4) != chunk.getZ()) continue;
                Block block = world.getBlockAt(x, y, z);
                if (!(block.getState() instanceof Sign sign)) {
                    continue;
                }
                if (!ChestShopSign.isValid(sign)) {
                    continue;
                }
                Container container = uBlock.findConnectedContainer(sign);
                if (container == null) continue;
                if (rememberShop(block, container)) {
                    saveShopRegistry();
                    reconcileShopChunkTickets();
                }
                String tag = displayTag(block);
                ShopDisplay existing = displays.get(tag);
                if (existing != null && existing.isHealthy()) continue;
                ItemStack item = parseShopItem(ChestShopSign.getItem(sign));
                if (item == null || item.getType().isAir()) continue;
                removeDisplays(block);
                createDisplay(block, container, item);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private Block signBlockFromTag(World world, String tag) {
        String suffix = tag.substring(displayTagPrefix(world).length());
        Matcher matcher = SIGN_COORDINATES.matcher(suffix);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return world.getBlockAt(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @EventHandler
    public void onShopCreated(ShopCreatedEvent event) {
        Block signBlock = event.getSign().getBlock();
        registerShop(signBlock, event.getContainer());
        scheduleShopRefresh(signBlock, event.getContainer());
    }

    private ItemStack parseShopItem(String itemCode) {
        ItemParseEvent parseEvent = new ItemParseEvent(itemCode);
        Bukkit.getPluginManager().callEvent(parseEvent);
        ItemStack item = parseEvent.getItem();
        if (item == null) {
            Material material = Material.matchMaterial(itemCode);
            if (material != null && !material.isAir()) {
                item = new ItemStack(material);
            }
        }
        return item;
    }

    private void createDisplay(Block signBlock, Container container, ItemStack item) {
        Location chestLocation = container.getLocation();
        double itemHeight = item.getType().isBlock() ? 0.95 : 1.10;
        Location itemLocation = chestLocation.clone().add(0.5, itemHeight, 0.5);
        Location textLocation = chestLocation.clone().add(0.5, 1.6, 0.5);
        String tag = displayTag(signBlock);

        ItemDisplay itemDisplay = chestLocation.getWorld()
                .spawn(itemLocation, ItemDisplay.class);
        itemDisplay.setItemStack(item.clone());
        itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        itemDisplay.setBillboard(Display.Billboard.FIXED);
        itemDisplay.setTeleportDuration(2);
        itemDisplay.setPersistent(true);
        itemDisplay.addScoreboardTag(tag);

        TextDisplay textDisplay = chestLocation.getWorld()
                .spawn(textLocation, TextDisplay.class);
        int shopQuantity = signBlock.getState() instanceof Sign sign
                ? ChestShopSign.getQuantity(sign)
                : 1;
        textDisplay.text(makeItemLabel(item, shopQuantity));
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setDefaultBackground(false);
        textDisplay.setShadowed(true);
        textDisplay.setSeeThrough(true);
        textDisplay.setVisibleByDefault(false);
        textDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(0.65f, 0.65f, 0.65f),
                new Quaternionf()
        ));
        textDisplay.setPersistent(true);
        textDisplay.addScoreboardTag(tag);

        ShopDisplay display = new ShopDisplay(
                signBlock, itemDisplay, textDisplay, itemLocation, item.clone()
        );
        displays.put(tag, display);
        display.start(tag);
    }

    @EventHandler
    public void onShopDestroyed(ShopDestroyedEvent event) {
        Block signBlock = event.getSign().getBlock();
        unregisterShop(signBlock);
        removeDisplays(signBlock);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopTransaction(TransactionEvent event) {
        restoreFormattedSignFor(event.getClient(), event.getSign().getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopSignInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Block shopSign = ChestShopSign.isValid(clickedBlock)
                ? clickedBlock
                : isChest(clickedBlock.getType())
                ? findAffectedShopSign(clickedBlock)
                : null;
        if (shopSign == null) {
            return;
        }

        Player player = event.getPlayer();
        // ChestShop may send its own sign update during this interaction.
        // Send our client-side format at MONITOR priority so it is the last
        // sign update in the normal event flow, then keep delayed fallbacks
        // for ChestShop versions that send another update later in the tick.
        restoreFormattedSignFor(player, shopSign);
        for (long delay : new long[]{1L, 3L, 10L}) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> restoreFormattedSignFor(player, shopSign), delay);
        }
    }

    private void restoreFormattedSignFor(Player player, Block signBlock) {
        if (!player.isOnline()
                || !player.getWorld().equals(signBlock.getWorld())
                || !ChestShopSign.isValid(signBlock)
                || player.getLocation().distanceSquared(
                signBlock.getLocation().clone().add(0.5, 0.5, 0.5)
        ) > SIGN_FORMAT_DISTANCE_SQUARED) {
            return;
        }

        ShopDisplay display = displays.get(displayTag(signBlock));
        if (display != null) {
            display.formattedSignViewers.add(player.getUniqueId());
            display.lastSignUpdateTick.put(player.getUniqueId(), display.ticks);
        }
        sendFormattedSign(player, signBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerPlaced(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        if (isChest(placedBlock.getType())) {
            Block shopSign = findAffectedShopSign(placedBlock);
            if (shopSign != null) {
                refreshShopNextTick(shopSign);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();

        if (ChestShopSign.isValid(brokenBlock)) {
            unregisterShop(brokenBlock);
            removeDisplays(brokenBlock);
            return;
        }

        Sign connectedSign = uBlock.getConnectedSign(brokenBlock);
        Block shopSign = connectedSign != null && ChestShopSign.isValid(connectedSign)
                ? connectedSign.getBlock()
                : null;
        if (connectedSign != null && ChestShopSign.isValid(connectedSign)) {
            removeDisplays(shopSign);
        }
        if (isChest(brokenBlock.getType())) {
            if (shopSign == null) {
                shopSign = findAffectedShopSign(brokenBlock);
            }
            if (shopSign != null) {
                refreshShopNextTick(shopSign);
            }
        }
    }

    private boolean isChest(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }

    private Block findAffectedShopSign(Block changedBlock) {
        Sign directlyConnected = uBlock.getConnectedSign(changedBlock);
        if (directlyConnected != null && ChestShopSign.isValid(directlyConnected)) {
            return directlyConnected.getBlock();
        }

        Block closestSign = null;
        double closestDistance = Double.MAX_VALUE;
        World world = changedBlock.getWorld();
        for (int x = changedBlock.getX() - 2; x <= changedBlock.getX() + 2; x++) {
            for (int y = changedBlock.getY() - 2; y <= changedBlock.getY() + 2; y++) {
                for (int z = changedBlock.getZ() - 2; z <= changedBlock.getZ() + 2; z++) {
                    Block candidate = world.getBlockAt(x, y, z);
                    if (!(candidate.getState() instanceof Sign sign)
                            || !ChestShopSign.isValid(sign)) {
                        continue;
                    }
                    Container connectedContainer = uBlock.findConnectedContainer(sign);
                    if (connectedContainer == null) {
                        continue;
                    }

                    Block chestBlock = connectedContainer.getBlock();
                    int distance = Math.abs(chestBlock.getX() - changedBlock.getX())
                            + Math.abs(chestBlock.getY() - changedBlock.getY())
                            + Math.abs(chestBlock.getZ() - changedBlock.getZ());
                    if (distance > 1) {
                        continue;
                    }
                    double signDistance = candidate.getLocation()
                            .distanceSquared(changedBlock.getLocation());
                    if (signDistance < closestDistance) {
                        closestDistance = signDistance;
                        closestSign = candidate;
                    }
                }
            }
        }
        return closestSign;
    }

    private void refreshShopNextTick(Block shopSign) {
        scheduleShopRefresh(shopSign, null);
    }

    private void scheduleShopRefresh(Block shopSign, Container fallbackContainer) {
        for (long delay : new long[]{1L, 5L, 20L}) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> refreshShop(shopSign, fallbackContainer), delay);
        }
    }

    private void refreshShop(Block shopSign, Container fallbackContainer) {
        if (!(shopSign.getState() instanceof Sign sign)
                || !ChestShopSign.isValid(sign)) {
            unregisterShop(shopSign);
            removeDisplays(shopSign);
            return;
        }

        Container container = uBlock.findConnectedContainer(sign);
        if (container == null && fallbackContainer != null
                && fallbackContainer.getBlock().getType() != Material.AIR) {
            container = fallbackContainer;
        }
        if (container == null) {
            return;
        }
        ItemStack item = parseShopItem(ChestShopSign.getItem(sign));
        if (item == null || item.getType().isAir()) {
            return;
        }

        registerShop(shopSign, container);
        Location expectedBase = container.getLocation().clone().add(
                0.5, item.getType().isBlock() ? 0.95 : 1.10, 0.5
        );
        ShopDisplay existing = displays.get(displayTag(shopSign));
        if (existing != null && existing.isHealthy()
                && existing.itemBase.getBlockX() == expectedBase.getBlockX()
                && existing.itemBase.getBlockY() == expectedBase.getBlockY()
                && existing.itemBase.getBlockZ() == expectedBase.getBlockZ()
                && existing.item.isSimilar(item)) {
            return;
        }
        removeDisplays(shopSign);
        createDisplay(shopSign, container, item);
        scheduleFormattedSign(shopSign, 5L);
    }

    private void scheduleFormattedSign(Block signBlock, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String tag = displayTag(signBlock);
            ShopDisplay display = displays.get(tag);
            if (display == null || !display.isHealthy()) return;

            for (Player player : signBlock.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(
                        signBlock.getLocation().clone().add(0.5, 0.5, 0.5)
                ) <= SIGN_FORMAT_DISTANCE_SQUARED) {
                    display.formattedSignViewers.add(player.getUniqueId());
                    display.lastSignUpdateTick.put(player.getUniqueId(), display.ticks);
                    sendFormattedSign(player, signBlock);
                }
            }
        }, delay);
    }

    private Component makeItemLabel(ItemStack item, int quantity) {
        Component label;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            label = item.getItemMeta().displayName();
        } else {
            label = Component.translatable(item.getType().translationKey());
        }

        label = label.append(Component.text(" (x" + quantity + ")", NamedTextColor.GRAY));

        Map<Enchantment, Integer> enchantments =
                new LinkedHashMap<>(item.getEnchantments());
        if (item.getItemMeta() instanceof EnchantmentStorageMeta bookMeta) {
            enchantments.putAll(bookMeta.getStoredEnchants());
        }
        if (!enchantments.isEmpty()) {
            Component enchantmentLine = Component.empty();
            boolean first = true;
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                if (!first) {
                    enchantmentLine = enchantmentLine.append(Component.text(", "));
                }
                enchantmentLine = enchantmentLine.append(
                        entry.getKey().displayName(entry.getValue())
                );
                first = false;
            }
            label = label.append(Component.newline()).append(enchantmentLine);
        }

        if (item.getItemMeta() instanceof Damageable damageable) {
            int maximumDurability = item.getType().getMaxDurability();
            if (maximumDurability > 0) {
                int remainingDurability = Math.max(
                        0, maximumDurability - damageable.getDamage()
                );
                label = label.append(Component.newline()).append(
                        Component.text("Durability: "
                                + remainingDurability + "/" + maximumDurability)
                );
            }
        }

        return label;
    }

    private ShopPrices parseShopPrices(String rawPrice) {
        Matcher normalDual = Pattern.compile(
                "(?i)B\\s*([0-9]+(?:[.,][0-9]+)?)\\s*:\\s*"
                        + "([0-9]+(?:[.,][0-9]+)?)\\s*S"
        ).matcher(rawPrice);
        if (normalDual.find()) {
            return new ShopPrices(normalDual.group(1), normalDual.group(2));
        }

        Matcher labeledDual = Pattern.compile(
                "(?i)B\\s*([0-9]+(?:[.,][0-9]+)?)\\s*:\\s*S\\s*"
                        + "([0-9]+(?:[.,][0-9]+)?)"
        ).matcher(rawPrice);
        if (labeledDual.find()) {
            return new ShopPrices(labeledDual.group(1), labeledDual.group(2));
        }

        Matcher matcher = Pattern.compile(
                "(?i)(?:^|\\s)([BS])\\s*([0-9]+(?:[.,][0-9]+)?)"
        ).matcher(rawPrice);
        String buyPrice = null;
        String sellPrice = null;
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase("B")) {
                buyPrice = matcher.group(2);
            } else {
                sellPrice = matcher.group(2);
            }
        }
        return new ShopPrices(buyPrice, sellPrice);
    }

    private String formatShopPrices(ShopPrices prices, boolean adminShop, String rawPrice) {
        if (prices.isDualPrice()) {
            return adminShop
                    ? "Buy " + prices.buyPrice() + " / Sell " + prices.sellPrice()
                    : "Sell " + prices.buyPrice() + " / Buy " + prices.sellPrice();
        }
        String price = prices.buyPrice() != null ? prices.buyPrice() : prices.sellPrice();
        return price == null ? rawPrice.trim() : price;
    }

    private String formatTradeQuantity(int quantity) {
        return Integer.toString(quantity);
    }

    private String getShopSignType(ShopPrices prices, boolean adminShop) {
        if (prices.isDualPrice()) {
            return "both";
        }
        boolean playerBuys = adminShop
                ? prices.buyPrice() != null
                : prices.sellPrice() != null;
        return playerBuys ? "buy" : "sell";
    }

    private void sendFormattedSign(Player player, Block signBlock) {
        if (!(signBlock.getState() instanceof Sign actualSign)) {
            return;
        }

        int quantity = ChestShopSign.getQuantity(actualSign);
        String rawPrice = ChestShopSign.getPrice(actualSign);
        ShopPrices prices = parseShopPrices(rawPrice);
        boolean adminShop = ChestShopSign.isAdminShop(actualSign);
        String currency = plugin.getConfig().getString(
                "shop-display.sign.currency-suffix", "LC");
        String shopPath = "shop-display.sign." + (adminShop ? "admin" : "regular");
        String shopType = getShopSignType(prices, adminShop);
        List<String> templates = plugin.getConfig().getStringList(
                shopPath + "." + shopType + ".lines");
        if (templates.isEmpty()) {
            // Backward compatibility for configs that still use regular.lines/admin.lines.
            templates = plugin.getConfig().getStringList(shopPath + ".lines");
        }
        if (templates.isEmpty()) {
            String typeLabel = switch (shopType) {
                case "buy" -> "Buy";
                case "sell" -> "Sell";
                default -> "Buy / Sell";
            };
            templates = adminShop
                    ? List.of("&2Admin Shop &8| &f" + typeLabel,
                    "&f%trade_quantity%", "&a%price% %currency%", "")
                    : List.of("&aShop &8| &f" + typeLabel,
                    "&f%trade_quantity%", "&a%price% %currency%", "&7%owner%");
        }

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("%buy_price%", prices.buyPrice() == null ? "" : prices.buyPrice());
        placeholders.put("%sell_price%", prices.sellPrice() == null ? "" : prices.sellPrice());
        placeholders.put("%price%", formatShopPrices(prices, adminShop, rawPrice));
        placeholders.put("%currency%", currency == null ? "" : currency);
        placeholders.put("%quantity%", Integer.toString(quantity));
        placeholders.put("%trade_quantity%", formatTradeQuantity(quantity));
        placeholders.put("%owner%", ChestShopSign.getOwner(actualSign));

        List<Component> lines = List.of(
                formatSignLine(templates, 0, placeholders),
                formatSignLine(templates, 1, placeholders),
                formatSignLine(templates, 2, placeholders),
                formatSignLine(templates, 3, placeholders)
        );
        player.sendSignChange(signBlock.getLocation(), lines);
    }

    private Component formatSignLine(List<String> templates, int index,
                                     Map<String, String> placeholders) {
        String line = index < templates.size() ? templates.get(index) : "";
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            line = line.replace(placeholder.getKey(), placeholder.getValue());
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(line);
    }

    private void sendRealSign(Player player, Block signBlock) {
        if (signBlock.getState() instanceof Sign signState) {
            player.sendBlockUpdate(signBlock.getLocation(), signState);
        }
    }

    private void removeDisplays(Block signBlock) {
        String tag = displayTag(signBlock);
        ShopDisplay display = displays.remove(tag);
        if (display != null) {
            display.stop();
        }

        for (Entity entity : signBlock.getWorld().getEntities()) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private void showOnlyTargetedShopLabel(Player player, ShopDisplay selectedDisplay) {
        UUID playerId = player.getUniqueId();
        for (ShopDisplay display : displays.values()) {
            if (display == selectedDisplay) {
                continue;
            }
            if (display.lastLookTick.remove(playerId) != null
                    && display.textDisplay.isValid()) {
                player.hideEntity(plugin, display.textDisplay);
            }
        }
    }

    private String displayTag(Block signBlock) {
        return displayTagPrefix(signBlock.getWorld())
                + "-" + signBlock.getX()
                + "-" + signBlock.getY()
                + "-" + signBlock.getZ();
    }

    private String displayTagPrefix(World world) {
        return "chestshoptge-"
                + world.getUID().toString().replace("-", "")
                + "-";
    }

    private final class ShopDisplay {

        private final Block signBlock;
        private final ItemDisplay itemDisplay;
        private final TextDisplay textDisplay;
        private final Location itemBase;
        private final ItemStack item;
        private final Map<UUID, Long> lastLookTick = new HashMap<>();
        private final Map<UUID, Long> lastSignUpdateTick = new HashMap<>();
        private final Set<UUID> formattedSignViewers = new HashSet<>();
        private BukkitTask task;
        private long ticks;

        private ShopDisplay(
                Block signBlock,
                ItemDisplay itemDisplay,
                TextDisplay textDisplay,
                Location itemBase,
                ItemStack item
        ) {
            this.signBlock = signBlock;
            this.itemDisplay = itemDisplay;
            this.textDisplay = textDisplay;
            this.itemBase = itemBase;
            this.item = item;
        }

        private void start(String tag) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!(signBlock.getState() instanceof Sign)
                        || !itemDisplay.isValid() || !textDisplay.isValid()) {
                    stop();
                    displays.remove(tag, this);
                    return;
                }

                ticks += 2;
                if (itemDisplay.isValid()) {
                    double bob = Math.sin(ticks * 0.08) * 0.08;
                    float yaw = (ticks * 3.0f) % 360.0f;
                    Location next = itemBase.clone().add(0, bob, 0);
                    next.setYaw(yaw);
                    next.setPitch(0);
                    itemDisplay.teleport(next);
                }

                updateSignFormatting();
                if (textDisplay.isValid()) {
                    updateTextVisibility();
                }
            }, 0L, 2L);
        }

        private boolean isHealthy() {
            return task != null && !task.isCancelled()
                    && itemDisplay.isValid() && textDisplay.isValid();
        }

        private void updateSignFormatting() {
            for (Player player : signBlock.getWorld().getPlayers()) {
                UUID playerId = player.getUniqueId();
                boolean nearby = player.getLocation().distanceSquared(
                        signBlock.getLocation().clone().add(0.5, 0.5, 0.5)
                ) <= SIGN_FORMAT_DISTANCE_SQUARED;
                if (nearby) {
                    boolean enteredRange = formattedSignViewers.add(playerId);
                    if (enteredRange) {
                        sendFormattedSign(player, signBlock);
                        lastSignUpdateTick.put(playerId, ticks);
                    }
                } else if (formattedSignViewers.remove(playerId)) {
                    lastSignUpdateTick.remove(playerId);
                }
            }

            formattedSignViewers.removeIf(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()
                        && player.getWorld().equals(signBlock.getWorld())) {
                    return false;
                }
                lastSignUpdateTick.remove(uuid);
                if (player != null && player.isOnline()) {
                    sendRealSign(player, signBlock);
                }
                return true;
            });
        }

        private void updateTextVisibility() {
            for (Player player : signBlock.getWorld().getPlayers()) {
                UUID playerId = player.getUniqueId();
                Block target = player.getTargetBlockExact(SIGN_LOOK_DISTANCE);
                boolean lookingAtSign = target != null
                        && target.getX() == signBlock.getX()
                        && target.getY() == signBlock.getY()
                        && target.getZ() == signBlock.getZ();

                if (lookingAtSign) {
                    showOnlyTargetedShopLabel(player, this);
                    if (!lastLookTick.containsKey(playerId)) {
                        player.showEntity(plugin, textDisplay);
                    }
                    lastLookTick.put(playerId, ticks);
                } else {
                    Long lastLooked = lastLookTick.get(playerId);
                    if (lastLooked != null
                            && ticks - lastLooked >= TEXT_LINGER_TICKS) {
                        lastLookTick.remove(playerId);
                        player.hideEntity(plugin, textDisplay);
                    }
                }
            }

            lastLookTick.keySet().removeIf(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()
                        && player.getWorld().equals(signBlock.getWorld())) {
                    return false;
                }
                if (player != null && player.isOnline()) {
                    player.hideEntity(plugin, textDisplay);
                }
                return true;
            });

        }

        private void stop() {
            if (task != null) {
                task.cancel();
            }
            for (UUID uuid : lastLookTick.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.hideEntity(plugin, textDisplay);
                }
            }
            lastLookTick.clear();
            for (UUID uuid : formattedSignViewers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    sendRealSign(player, signBlock);
                }
            }
            formattedSignViewers.clear();
            lastSignUpdateTick.clear();
        }

    }
}
