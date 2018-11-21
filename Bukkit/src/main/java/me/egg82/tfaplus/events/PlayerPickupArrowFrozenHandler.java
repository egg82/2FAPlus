package me.egg82.tfaplus.events;

import java.util.function.Consumer;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerPickupArrowFrozenHandler implements Consumer<PlayerPickupArrowEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void accept(PlayerPickupArrowEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
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

        if (cachedConfig.getFreeze().getDrops()) {
            event.setCancelled(true);
        }
    }
}
