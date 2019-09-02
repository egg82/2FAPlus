package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.util.List;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.services.CollectionProvider;
import ninja.egg82.events.BukkitEvents;
import ninja.egg82.tuples.longs.LongObjectPair;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class HOTPEvents extends EventHolder {
    private final Plugin plugin;
    private final CommandManager commandManager;

    public HOTPEvents(Plugin plugin, CommandManager commandManager) {
        this.plugin = plugin;
        this.commandManager = commandManager;

        events.add(
                BukkitEvents.subscribe(plugin, AsyncPlayerChatEvent.class, EventPriority.LOWEST)
                        .filter(e -> CollectionProvider.getHOTPFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::hotpSeek)
        );

        events.add(
                BukkitEvents.subscribe(plugin, PlayerQuitEvent.class, EventPriority.HIGH)
                        .handler(e -> CollectionProvider.getHOTPFrozen().remove(e.getPlayer().getUniqueId()))
        );
    }

    private void hotpSeek(AsyncPlayerChatEvent event) {
        String message = event.getMessage().replaceAll("\\s+", "").trim();
        if (message.matches("\\d+")) {
            event.setCancelled(true);

            LongObjectPair<List<String>> pair = CollectionProvider.getHOTPFrozen().get(event.getPlayer().getUniqueId());
            if (pair == null) {
                return;
            }

            pair.getSecond().add(message);

            if (pair.getSecond().size() >= pair.getFirst()) {
                CollectionProvider.getHOTPFrozen().remove(event.getPlayer().getUniqueId());
                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.RESYNC__BEGIN);

                try {
                    api.seekHOTPCounter(event.getPlayer().getUniqueId(), pair.getSecond());
                } catch (APIException ex) {
                    if (ex.isHard()) {
                        logger.error(ex.getMessage(), ex);
                        commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__INTERNAL);
                    } else {
                        commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.RESYNC__FAILURE);
                    }
                    return;
                }

                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.RESYNC__SUCCESS);
            } else {
                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.RESYNC__MORE, "{codes}", String.valueOf(pair.getFirst() - pair.getSecond().size()));
            }
        }
    }
}
