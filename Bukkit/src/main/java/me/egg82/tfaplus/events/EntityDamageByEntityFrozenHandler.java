package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityDamageByEntityFrozenHandler implements Consumer<EntityDamageByEntityEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandManager commandManager;

    public EntityDamageByEntityFrozenHandler(CommandManager commandManager) { this.commandManager = commandManager; }

    public void accept(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getDamager().getUniqueId())) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getAttack()) {
            commandManager.getCommandIssuer(event.getDamager()).sendError(Message.ERROR__NEED_AUTH_ACTION);
            event.setCancelled(true);
        }
    }
}
