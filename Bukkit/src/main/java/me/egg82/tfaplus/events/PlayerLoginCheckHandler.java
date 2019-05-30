package me.egg82.tfaplus.events;

import me.egg82.tfaplus.TFAAPI;
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

import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Consumer;

public class PlayerLoginCheckHandler implements Consumer<PlayerLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TFAAPI api = TFAAPI.getInstance();

    private final Plugin plugin;

    public PlayerLoginCheckHandler(Plugin plugin) { this.plugin = plugin; }

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
            return;
        }

        if (cachedConfig.get().getDebug()) {
            logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is set to be checked on login.");
        }

        if (cachedConfig.get().getIgnored().contains(ip) || cachedConfig.get().getIgnored().contains(event.getPlayer().getUniqueId().toString())) {
            return;
        }

        if (!api.isRegistered(event.getPlayer().getUniqueId())) {
            if (cachedConfig.get().getForceAuth()) {
                if (cachedConfig.get().getDebug()) {
                    logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is not registered, and registration is required. Kicking with defined message.");
                }
                kickPlayer(config.get(), event);
            } else {
                if (cachedConfig.get().getDebug()) {
                    logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is not registered, and registration is not required. Ignoring.");
                }
            }
            return;
        }

        if (InternalAPI.getLogin(event.getPlayer().getUniqueId(), ip, cachedConfig.get().getIPTime())) {
            if (cachedConfig.get().getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " has verified from this IP recently. Ignoring.");
            }
            return;
        }

        CollectionProvider.getFrozen().put(event.getPlayer().getUniqueId(), 0L);
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Please enter your 2FA code into the chat."));
        if (cachedConfig.get().getDebug()) {
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
