package me.egg82.tfaplus.events;

import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.LogUtil;
import org.bukkit.ChatColor;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryClickFrozenHandler implements Consumer<InventoryClickEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void accept(InventoryClickEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getWhoClicked().getUniqueId())) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getInventory()) {
            event.getWhoClicked().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "You must first authenticate with your 2FA code before doing that!");
            event.setCancelled(true);
        }
    }
}
