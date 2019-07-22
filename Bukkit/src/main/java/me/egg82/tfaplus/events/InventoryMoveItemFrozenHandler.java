package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryMoveItemFrozenHandler implements Consumer<InventoryMoveItemEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandManager commandManager;

    public InventoryMoveItemFrozenHandler(CommandManager commandManager) { this.commandManager = commandManager; }

    public void accept(InventoryMoveItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getSource().getHolder() instanceof Player)) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(((Player) event.getSource().getHolder()).getUniqueId())) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getInventory()) {
            commandManager.getCommandIssuer(event.getSource().getHolder()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }
}
