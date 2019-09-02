package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import com.google.common.reflect.TypeToken;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.hooks.PlaceholderAPIHook;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.events.BukkitEvents;
import ninja.egg82.service.ServiceLocator;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class LoginEvents extends EventHolder {
    private final Plugin plugin;
    private final CommandManager commandManager;

    public LoginEvents(Plugin plugin, CommandManager commandManager) {
        this.plugin = plugin;
        this.commandManager = commandManager;

        events.add(
                BukkitEvents.subscribe(plugin, AsyncPlayerPreLoginEvent.class, EventPriority.HIGH)
                        .handler(this::cacheLogin)
        );

        events.add(
                BukkitEvents.subscribe(plugin, PlayerLoginEvent.class, EventPriority.HIGHEST)
                        .handler(this::checkLogin)
        );

        events.add(
                BukkitEvents.subscribe(plugin, AsyncPlayerChatEvent.class, EventPriority.LOWEST)
                        .filter(e -> CollectionProvider.getFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::verifyCode)
        );

        events.add(
                BukkitEvents.subscribe(plugin, PlayerQuitEvent.class, EventPriority.HIGH)
                        .handler(e -> CollectionProvider.getFrozen().remove(e.getPlayer().getUniqueId()))
        );
    }

    private void cacheLogin(AsyncPlayerPreLoginEvent event) {
        // Basically this event is here to cache the player data
        // PlayerJoin gets ahold of it
        String ip = getIp(event.getAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        if (cachedConfig.get().getIgnored().contains(ip)) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getUniqueId() + ChatColor.YELLOW + " is using an ignored IP " + ChatColor.WHITE + ip +  ChatColor.YELLOW + ". Ignoring.");
            }
            return;
        }

        if (cachedConfig.get().getIgnored().contains(event.getUniqueId().toString())) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getUniqueId() + ChatColor.YELLOW + " is an ignored UUID. Ignoring.");
            }
            return;
        }

        try {
            api.isRegistered(event.getUniqueId()); // Calling this will cache the player data internally, even if the value is unused
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    private void checkLogin(PlayerLoginEvent event) {
        String ip = getIp(event.getAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        Optional<Configuration> config = ConfigUtil.getConfig();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!config.isPresent() || !cachedConfig.isPresent()) {
            return;
        }

        if (!event.getPlayer().hasPermission("2faplus.check")) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " does not have check perm node. Ignoring.");
            }
            return;
        }

        if (cachedConfig.get().getIgnored().contains(ip) || cachedConfig.get().getIgnored().contains(event.getPlayer().getUniqueId().toString())) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " has bypass in config. Ignoring.");
            }
            return;
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Checking " + ChatColor.WHITE + event.getPlayer().getName());
        }

        try {
            if (!api.isRegistered(event.getPlayer().getUniqueId())) {
                if (cachedConfig.get().getForceAuth()) {
                    if (ConfigUtil.getDebugOrFalse()) {
                        logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is not registered, and registration is required. Kicking with defined message.");
                    }
                    kickPlayer(config.get(), event);
                } else {
                    if (ConfigUtil.getDebugOrFalse()) {
                        logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is not registered, and registration is not required. Ignoring.");
                    }
                }
                return;
            }
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            if (cachedConfig.get().getForceAuth()) {
                kickPlayer(config.get(), event); // Kick on exception
            }
            return;
        }

        try {
            if (InternalAPI.getLogin(event.getPlayer().getUniqueId(), ip)) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " has verified from this IP recently. Ignoring.");
                }
                return;
            }
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
        }

        CollectionProvider.getFrozen().put(event.getPlayer().getUniqueId(), 0L);
        Bukkit.getScheduler().runTask(plugin, () -> commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.PLAYER__ENTER_CODE));
        if (ConfigUtil.getDebugOrFalse()) {
            logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " has been sent a verification request.");
        }
    }

    private String getIp(InetAddress address) {
        if (address == null) {
            return null;
        }

        return address.getHostAddress();
    }

    private void kickPlayer(Configuration config, PlayerLoginEvent event) {
        Optional<PlaceholderAPIHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }

        event.setResult(PlayerLoginEvent.Result.KICK_OTHER);

        if (placeholderapi.isPresent()) {
            event.setKickMessage(placeholderapi.get().withPlaceholders(event.getPlayer(), config.getNode("2fa", "no-auth-kick-message").getString("")));
        } else {
            event.setKickMessage(config.getNode("2fa", "no-auth-kick-message").getString(""));
        }
    }

    private void verifyCode(AsyncPlayerChatEvent event) {
        Optional<Configuration> config = ConfigUtil.getConfig();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!config.isPresent() || !cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        String message = event.getMessage().replaceAll("\\s+", "").trim();
        if (!message.matches("\\d+")) {
            if (cachedConfig.get().getFreeze().getChat()) {
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.VERIFY__BEGIN);
        try {
            if (!api.verify(event.getPlayer().getUniqueId(), message)) {
                long attempts = CollectionProvider.getFrozen().compute(event.getPlayer().getUniqueId(), (k, v) -> {
                    if (v == null) {
                        return 1L;
                    }
                    return v + 1L;
                });

                if (cachedConfig.get().getMaxAttempts() <= 0L || attempts < cachedConfig.get().getMaxAttempts()) {
                    commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.VERIFY__FAILURE_INVALID);
                } else {
                    if (event.isAsynchronous()) {
                        Bukkit.getScheduler().runTask(plugin, () -> tryRunCommand(config.get(), event.getPlayer(), false));
                        Bukkit.getScheduler().runTask(plugin, () -> tryKickPlayer(config.get(), event.getPlayer()));
                    } else {
                        tryRunCommand(config.get(), event.getPlayer(), false);
                        tryKickPlayer(config.get(), event.getPlayer());
                    }
                }
                return;
            }
        } catch (APIException ex) {
            if (ex.isHard()) {
                logger.error(ex.getMessage(), ex);
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__INTERNAL);
            } else {
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.VERIFY__FAILURE_ERROR, "{error}", ex.getMessage());
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
        commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.VERIFY__SUCCESS);

        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> tryRunCommand(config.get(), event.getPlayer(), true));
        } else {
            tryRunCommand(config.get(), event.getPlayer(), true);
        }
    }

    private void tryRunCommand(Configuration config, Player player, boolean success) {
        Optional<PlaceholderAPIHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }

        List<String> commands;
        try {
            commands = success ? config.getNode("2fa", "success-commands").getList(TypeToken.of(String.class)) : config.getNode("2fa", "fail-commands").getList(TypeToken.of(String.class));
        } catch (ObjectMappingException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }
        if (commands.isEmpty()) {
            return;
        }

        for (String command : commands) {
            if (command.isEmpty()) {
                continue;
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
    }

    private void tryKickPlayer(Configuration config, Player player) {
        Optional<PlaceholderAPIHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }

        String message = config.getNode("2fa", "fail-kick-message").getString("");
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
