package me.egg82.tfaplus.events;

import java.net.InetAddress;
import java.util.function.Consumer;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncPlayerPreLoginCacheHandler implements Consumer<AsyncPlayerPreLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TFAAPI api = TFAAPI.getInstance();

    public void accept(AsyncPlayerPreLoginEvent event) {
        // Basically this event is here to cache the player data
        // PlayerJoin gets ahold of it
        String ip = getIp(event.getAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (cachedConfig.getIgnored().contains(ip)) {
            if (cachedConfig.getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getUniqueId() + ChatColor.YELLOW + " is using an ignored IP " + ChatColor.WHITE + ip +  ChatColor.YELLOW + ". Ignoring.");
            }
            return;
        }

        if (cachedConfig.getIgnored().contains(event.getUniqueId().toString())) {
            if (cachedConfig.getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getUniqueId() + ChatColor.YELLOW + " is an ignored UUID. Ignoring.");
            }
            return;
        }

        api.isRegistered(event.getUniqueId()); // Calling this will cache the player data internally, even if the value is unused
    }

    private String getIp(InetAddress address) {
        if (address == null) {
            return null;
        }

        return address.getHostAddress();
    }
}
