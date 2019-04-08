package me.egg82.tfaplus.events;

import me.egg82.tfaplus.services.CollectionProvider;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

public class PlayerQuitFrozenHandler implements Consumer<PlayerQuitEvent> {
    public void accept(PlayerQuitEvent event) {
        CollectionProvider.getFrozen().remove(event.getPlayer().getUniqueId());
    }
}
