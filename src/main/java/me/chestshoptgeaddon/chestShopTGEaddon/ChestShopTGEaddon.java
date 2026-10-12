package me.chestshoptgeaddon.chestShopTGEaddon;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChestShopTGEaddon extends JavaPlugin {

    private ShopDisplayListener shopDisplayListener;
    private GamblingDisplayManager gamblingDisplayManager;
    private GamblingShopListener gamblingShopListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Plugin chestShop = getServer()
                .getPluginManager()
                .getPlugin("ChestShop");

        if (chestShop == null) {
            getLogger().severe("ChestShop was not found. Disabling ChestShopTGEaddon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GamblingRewardDefaults.generateIfRequested(this);

        shopDisplayListener = new ShopDisplayListener(this);
        getServer().getPluginManager().registerEvents(shopDisplayListener, this);

        gamblingDisplayManager = new GamblingDisplayManager(this);
        getServer().getPluginManager().registerEvents(gamblingDisplayManager, this);

        gamblingShopListener = new GamblingShopListener(this, gamblingDisplayManager);
        getServer().getPluginManager().registerEvents(gamblingShopListener, this);

        ChestShopTGEaddonCommand command = new ChestShopTGEaddonCommand(this);
        getCommand("chestshoptgeaddon").setExecutor(command);
        getCommand("chestshoptgeaddon").setTabCompleter(command);

        shopDisplayListener.restoreLoadedDisplays();
        gamblingDisplayManager.restoreLoadedDisplays();
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
        GamblingRewardDefaults.generateIfRequested(this);
        gamblingDisplayManager.reloadSettings();
        gamblingShopListener.refreshLoadedSignTemplates();
        shopDisplayListener.refreshFormattedSigns();
    }
}

