package me.egg82.tfaplus.events;

import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import me.egg82.tfaplus.utils.RabbitMQUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
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

    public AsyncPlayerChatFrozenHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    public void accept(AsyncPlayerChatEvent event) {
        if (!CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        String message = event.getMessage().replaceAll("\\s+", "").trim();
        if (!message.matches("\\d+")) {
            if (cachedConfig.getFreeze().getChat()) {
                event.getPlayer().sendMessage(ChatColor.DARK_RED + "You must first authenticate with your 2FA code before chatting!");
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        Optional<Boolean> result = api.verify(event.getPlayer().getUniqueId(), message);

        if (!result.isPresent()) {
            event.getPlayer().sendMessage(ChatColor.DARK_RED + "Something went wrong while validating your 2FA code.");
            return;
        }

        if (!result.get()) {
            long attempts = CollectionProvider.getFrozen().compute(event.getPlayer().getUniqueId(), (k, v) -> {
                if (v == null) {
                    return 1L;
                }
                return v + 1L;
            });

            if (cachedConfig.getMaxAttempts() <= 0L || attempts < cachedConfig.getMaxAttempts()) {
                event.getPlayer().sendMessage(ChatColor.DARK_RED + "Your 2FA code was invalid!");
            } else {
                if (event.isAsynchronous()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        kickPlayer(config, event.getPlayer());
                    });
                } else {
                    kickPlayer(config, event.getPlayer());
                }
            }
            return;
        }

        setLogin(config, cachedConfig, event.getPlayer().getUniqueId(), getIp(event.getPlayer()));
        CollectionProvider.getFrozen().remove(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(ChatColor.GREEN + "Your 2FA code was successfully verified!");
    }

    private void setLogin(Configuration config, CachedConfigValues cachedConfig, UUID uuid, String ip) {
        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            InternalAPI.setLogin(uuid, ip, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
            return;
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        InternalAPI.setLogin(uuid, ip, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
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
