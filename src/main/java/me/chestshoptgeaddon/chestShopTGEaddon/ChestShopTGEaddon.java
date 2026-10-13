package me.chestshoptgeaddon.chestShopTGEaddon;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

public final class ChestShopTGEaddon extends JavaPlugin {

    private ShopDisplayListener shopDisplayListener;
    private GamblingDisplayManager gamblingDisplayManager;
    private GamblingShopListener gamblingShopListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginManager pluginManager = getServer().getPluginManager();

        Plugin chestShop = pluginManager.getPlugin("ChestShop");
        if (chestShop == null || !chestShop.isEnabled()) {
            getLogger().severe("ChestShop was not found. Disabling ChestShopTGEaddon.");
            pluginManager.disablePlugin(this);
            return;
        }

        shopDisplayListener = new ShopDisplayListener(this);
        pluginManager.registerEvents(shopDisplayListener, this);
        shopDisplayListener.restoreLoadedDisplays();

        Plugin xconomy = pluginManager.getPlugin("XConomy");

        if (xconomy != null && xconomy.isEnabled()) {
            GamblingRewardDefaults.generateIfRequested(this);

            gamblingDisplayManager = new GamblingDisplayManager(this);
            pluginManager.registerEvents(gamblingDisplayManager, this);

            gamblingShopListener = new GamblingShopListener(
                    this, gamblingDisplayManager
            );
            pluginManager.registerEvents(gamblingShopListener, this);

            gamblingDisplayManager.restoreLoadedDisplays();

            getLogger().info("Gambling Chests enabled (XConomy found).");
        } else {
            getLogger().warning(
                    "XConomy was not found. Gambling Chests are disabled. "
                            + "Regular shop features remain enabled."
            );
        }

        ChestShopTGEaddonCommand command = new ChestShopTGEaddonCommand(this);
        getCommand("chestshoptgeaddon").setExecutor(command);
        getCommand("chestshoptgeaddon").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (gamblingDisplayManager != null) {
            gamblingDisplayManager.shutdown();
        }
        if (shopDisplayListener != null) {
            shopDisplayListener.shutdown();
        }
    }

    void reloadPluginConfiguration() {
        reloadConfig();

        if (gamblingDisplayManager != null) {
            GamblingRewardDefaults.generateIfRequested(this);
            gamblingDisplayManager.reloadSettings();
            gamblingShopListener.refreshLoadedSignTemplates();
        }

        if (shopDisplayListener != null) {
            shopDisplayListener.refreshFormattedSigns();
        }
    }
}
