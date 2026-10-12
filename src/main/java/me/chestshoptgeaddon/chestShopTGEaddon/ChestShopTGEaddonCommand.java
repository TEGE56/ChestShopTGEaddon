package me.chestshoptgeaddon.chestShopTGEaddon;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class ChestShopTGEaddonCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "chestshoptgeaddon.reload";
    private final ChestShopTGEaddon plugin;

    public ChestShopTGEaddonCommand(ChestShopTGEaddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            send(sender, "commands.reload.usage", "&cUsage: /chestshoptgeaddon reload");
            return true;
        }

        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            send(sender, "commands.reload.no-permission",
                    "&cYou do not have permission to use this command.");
            return true;
        }

        try {
            plugin.reloadPluginConfiguration();
            send(sender, "commands.reload.success",
                    "&aChestShopTGEaddon configuration reloaded.");
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Configuration reload failed: " + exception.getMessage());
            send(sender, "commands.reload.failure",
                    "&cConfiguration reload failed. Check the console.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission(RELOAD_PERMISSION) || args.length != 1) {
            return List.of();
        }
        return "reload".startsWith(args[0].toLowerCase(Locale.ROOT))
                ? List.of("reload") : List.of();
    }

    private void send(CommandSender sender, String path, String fallback) {
        String message = plugin.getConfig().getString(path, fallback);
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
