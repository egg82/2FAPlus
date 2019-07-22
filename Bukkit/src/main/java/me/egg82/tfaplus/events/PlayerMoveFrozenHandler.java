package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerMoveFrozenHandler implements Consumer<PlayerMoveEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LoadingCache<UUID, Boolean> recentlyMoved = Caffeine.newBuilder().expireAfterWrite(10L, TimeUnit.SECONDS).build(k -> Boolean.FALSE);

    private final CommandManager commandManager;

    public PlayerMoveFrozenHandler(CommandManager commandManager) { this.commandManager = commandManager; }

    public void accept(PlayerMoveEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        if (areEqualXZ(event.getFrom(), event.getTo()) && event.getTo().getY() < event.getFrom().getY()) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.get().getFreeze().getMove()) {
            if (!recentlyMoved.get(event.getPlayer().getUniqueId())) {
                recentlyMoved.put(event.getPlayer().getUniqueId(), Boolean.TRUE);
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_MOVE);
            }
            event.setCancelled(true);
        }
    }

    public static boolean areEqualXZ(Location from, Location to) {
        return from.getWorld().equals(to.getWorld()) && from.getX() == to.getX() && from.getZ() == to.getZ();
    }
}
