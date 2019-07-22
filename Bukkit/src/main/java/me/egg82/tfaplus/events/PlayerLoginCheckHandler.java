package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.net.InetAddress;
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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerLoginCheckHandler implements Consumer<PlayerLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TFAAPI api = TFAAPI.getInstance();

    private final Plugin plugin;
    private final CommandManager commandManager;

    public PlayerLoginCheckHandler(Plugin plugin, CommandManager commandManager) {
        this.plugin = plugin;
        this.commandManager = commandManager;
    }

    public void accept(PlayerLoginEvent event) {
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
}
