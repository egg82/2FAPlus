package me.egg82.tfaplus.events;

import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
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

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getDrops()) {
            event.setCancelled(true);
        }
    }
}
