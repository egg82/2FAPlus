package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.hooks.PlaceholderAPIHook;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.tuples.longs.LongObjectPair;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPlayerChatFrozenHandler implements Consumer<AsyncPlayerChatEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TFAAPI api = TFAAPI.getInstance();

    private final Plugin plugin;

    public AsyncPlayerChatFrozenHandler(Plugin plugin) { this.plugin = plugin; }

    public void accept(AsyncPlayerChatEvent event) {
        if (CollectionProvider.getHOTPFrozen().containsKey(event.getPlayer().getUniqueId())) {
            String message = event.getMessage().replaceAll("\\s+", "").trim();
            if (message.matches("\\d+")) {
                event.setCancelled(true);

                LongObjectPair<List<String>> pair = CollectionProvider.getHOTPFrozen().get(event.getPlayer().getUniqueId());
                if (pair == null) {
                    return;
                }

                pair.getSecond().add(message);

                if (pair.getSecond().size() >= pair.getFirst()) {
                    CollectionProvider.getHOTPFrozen().remove(event.getPlayer().getUniqueId());
                    event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Attempting to re-synchronize your counter, please wait..");

                    try {
                        api.seekHOTPCounter(event.getPlayer().getUniqueId(), pair.getSecond());
                    } catch (APIException ex) {
                        if (ex.isHard()) {
                            logger.error(ex.getMessage(), ex);
                            CommandManager.getCurrentCommandManager().getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__INTERNAL);
                        } else {
                            event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "Your counter could not be re-synchronized using the codes provided. Please try again.");
                        }
                        return;
                    }

                    event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Successfully re-synchronized your counter.");
                } else {
                    event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.WHITE + (pair.getFirst() - pair.getSecond().size()) + ChatColor.YELLOW + " more code" + (pair.getFirst() - pair.getSecond().size() > 1 ? "s" : "") + " to go!");
                }
            }

            return;
        }

        if (CollectionProvider.getCommandFrozen().containsKey(event.getPlayer().getUniqueId())) {
            String message = event.getMessage().replaceAll("\\s+", "").trim();
            if (message.matches("\\d+")) {
                event.setCancelled(true);

                event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Verifying your 2FA code, please wait..");
                try {
                    if (!api.verify(event.getPlayer().getUniqueId(), message)) {
                        event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "Your 2FA code was invalid! Please try again.");
                        return;
                    }
                } catch (APIException ex) {
                    if (ex.isHard()) {
                        logger.error(ex.getMessage(), ex);
                        CommandManager.getCurrentCommandManager().getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__INTERNAL);
                    } else {
                        event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "Something went wrong while validating your 2FA code: " + ex.getMessage());
                    }
                    return;
                }

                String command = CollectionProvider.getCommandFrozen().remove(event.getPlayer().getUniqueId());
                event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Your 2FA code was successfully verified! Running the command..");
                if (event.isAsynchronous()) {
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().dispatchCommand(event.getPlayer(), command));
                } else {
                    Bukkit.getServer().dispatchCommand(event.getPlayer(), command);
                }
            }

            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        Optional<Configuration> config = ConfigUtil.getConfig();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!config.isPresent() || !cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        String message = event.getMessage().replaceAll("\\s+", "").trim();
        if (!message.matches("\\d+")) {
            if (cachedConfig.get().getFreeze().getChat()) {
                CommandManager.getCurrentCommandManager().getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Verifying your 2FA code, please wait..");
        try {
            if (!api.verify(event.getPlayer().getUniqueId(), message)) {
                long attempts = CollectionProvider.getFrozen().compute(event.getPlayer().getUniqueId(), (k, v) -> {
                    if (v == null) {
                        return 1L;
                    }
                    return v + 1L;
                });

                if (cachedConfig.get().getMaxAttempts() <= 0L || attempts < cachedConfig.get().getMaxAttempts()) {
                    event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "Your 2FA code was invalid! Please try again.");
                } else {
                    if (event.isAsynchronous()) {
                        Bukkit.getScheduler().runTask(plugin, () -> tryRunCommand(config.get(), event.getPlayer()));
                        Bukkit.getScheduler().runTask(plugin, () -> tryKickPlayer(config.get(), event.getPlayer()));
                    } else {
                        tryRunCommand(config.get(), event.getPlayer());
                        tryKickPlayer(config.get(), event.getPlayer());
                    }
                }
                return;
            }
        } catch (APIException ex) {
            if (ex.isHard()) {
                logger.error(ex.getMessage(), ex);
                CommandManager.getCurrentCommandManager().getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__INTERNAL);
            } else {
                event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "Something went wrong while validating your 2FA code: " + ex.getMessage());
            }
            return;
        }

        try {
            InternalAPI.setLogin(event.getPlayer().getUniqueId(), getIp(event.getPlayer()));
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        CollectionProvider.getFrozen().remove(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Your 2FA code was successfully verified!");
    }

    private void tryRunCommand(Configuration config, Player player) {
        Optional<PlaceholderAPIHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }

        String command = config.getNode("2fa", "too-many-attempts-command").getString("");
        if (command.isEmpty()) {
            return;
        }

        command = command.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString());
        if (command.charAt(0) == '/') {
            command = command.substring(1);
        }

        if (placeholderapi.isPresent()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholderapi.get().withPlaceholders(player, command));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void tryKickPlayer(Configuration config, Player player) {
        Optional<PlaceholderAPIHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }

        String message = config.getNode("2fa", "too-many-attempts-kick-message").getString("");
        if (message.isEmpty()) {
            return;
        }

        if (placeholderapi.isPresent()) {
            player.kickPlayer(placeholderapi.get().withPlaceholders(player, message));
        } else {
            player.kickPlayer(message);
        }
    }

    private String getIp(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null) {
            return null;
        }
        InetAddress host = address.getAddress();
        if (host == null) {
            return null;
        }
        return host.getHostAddress();
    }
}
