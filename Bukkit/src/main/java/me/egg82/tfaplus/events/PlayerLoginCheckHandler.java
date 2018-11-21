package me.egg82.tfaplus.events;

import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.hooks.PlaceholderAPIHook;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.utils.LogUtil;
import me.egg82.tfaplus.utils.RabbitMQUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerLoginCheckHandler implements Consumer<PlayerLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TFAAPI api = TFAAPI.getInstance();

    public void accept(PlayerLoginEvent event) {
        String ip = getIp(event.getAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        Configuration config;
        CachedConfigValues cachedConfig;

        try {
            config = ServiceLocator.get(Configuration.class);
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (!event.getPlayer().hasPermission("2faplus.check")) {
            return;
        }

        if (cachedConfig.getDebug()) {
            logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is set to be checked on login.");
        }

        if (cachedConfig.getIgnored().contains(ip) || cachedConfig.getIgnored().contains(event.getPlayer().getUniqueId().toString())) {
            return;
        }

        if (!api.isRegistered(event.getPlayer().getUniqueId())) {
            if (cachedConfig.getForceAuth()) {
                if (cachedConfig.getDebug()) {
                    logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is not registered, and registration is required. Kicking with defined message.");
                }
                kickPlayer(config, event.getPlayer());
            } else {
                if (cachedConfig.getDebug()) {
                    logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is not registered, and registration is not required. Ignoring.");
                }
            }
            return;
        }

        if (canLogin(config, cachedConfig, event.getPlayer().getUniqueId(), ip)) {
            if (cachedConfig.getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " has verified from this IP before. Ignoring.");
            }
            return;
        }

        CollectionProvider.getFrozen().put(event.getPlayer().getUniqueId(), 0L);
        event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Please enter your 2FA code into the chat.");
        if (cachedConfig.getDebug()) {
            logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " has been sent a verification request.");
        }
    }

    private String getIp(InetAddress address) {
        if (address == null) {
            return null;
        }

        return address.getHostAddress();
    }

    private boolean canLogin(Configuration config, CachedConfigValues cachedConfig, UUID uuid, String ip) {
        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return InternalAPI.getLogin(uuid, ip, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return InternalAPI.getLogin(uuid, ip, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    private void kickPlayer(Configuration config, Player player) {
        Optional<PlaceholderAPIHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }

        if (placeholderapi.isPresent()) {
            player.kickPlayer(placeholderapi.get().withPlaceholders(player, config.getNode("2fa", "too-many-attempts-kick-message").getString("")));
        } else {
            player.kickPlayer(config.getNode("2fa", "too-many-attempts-kick-message").getString(""));
        }
    }
}
