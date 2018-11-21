package me.egg82.tfaplus.events;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerTeleportFrozenHandler implements Consumer<PlayerTeleportEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LoadingCache<UUID, Boolean> recentlyTeleported = Caffeine.newBuilder().expireAfterWrite(10L, TimeUnit.SECONDS).build(k -> Boolean.FALSE);

    public void accept(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        if (areEqualXZ(event.getFrom(), event.getTo()) && event.getTo().getY() < event.getFrom().getY()) {
            return;
        }

        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.getFreeze().getMove()) {
            if (!recentlyTeleported.get(event.getPlayer().getUniqueId())) {
                recentlyTeleported.put(event.getPlayer().getUniqueId(), Boolean.TRUE);
                event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "You must first authenticate with your 2FA code before teleporting!");
            }
            event.setCancelled(true);
        }
    }

    public static boolean areEqualXZ(Location from, Location to) {
        return from.getWorld().equals(to.getWorld()) && from.getX() == to.getX() && from.getZ() == to.getZ();
    }
}
