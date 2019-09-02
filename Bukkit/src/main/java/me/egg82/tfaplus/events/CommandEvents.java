package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.util.Optional;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import ninja.egg82.events.BukkitEvents;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class CommandEvents extends EventHolder {
    private final Plugin plugin;
    private final CommandManager commandManager;

    public CommandEvents(Plugin plugin, CommandManager commandManager) {
        this.plugin = plugin;
        this.commandManager = commandManager;

        events.add(
                BukkitEvents.subscribe(plugin, PlayerCommandPreprocessEvent.class, EventPriority.LOWEST)
                        .handler(this::checkCommand)
        );

        events.add(
                BukkitEvents.subscribe(plugin, AsyncPlayerChatEvent.class, EventPriority.LOWEST)
                        .filter(e -> CollectionProvider.getCommandFrozen().containsKey(e.getPlayer().getUniqueId()))
                        .handler(this::verifyCode)
        );

        events.add(
                BukkitEvents.subscribe(plugin, PlayerQuitEvent.class, EventPriority.HIGH)
                        .handler(e -> CollectionProvider.getCommandFrozen().remove(e.getPlayer().getUniqueId()))
        );
    }

    private void checkCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
            if (cachedConfig.get().getFreeze().getCommand()) {
                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__NEED_AUTH_ACTION);
                event.setCancelled(true);
            }
        } else {
            String message = event.getMessage().substring(1);

            int colon = message.indexOf(':');
            String split = message.substring(colon + 1);

            if (colon > -1) {
                for (String command : cachedConfig.get().getCommands()) {
                    command = command.trim() + " ";

                    if (split.startsWith(command)) {
                        if (isRegister(split) || isCheck(message)) {
                            break; // Skip authentication for 2FA registration/check commands
                        }

                        try {
                            if (!api.isRegistered(event.getPlayer().getUniqueId())) {
                                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.PLAYER__ERROR_PROTECTED);
                                event.setCancelled(true);
                            } else if (!api.isVerified(event.getPlayer().getUniqueId(), true)) {
                                CollectionProvider.getCommandFrozen().put(event.getPlayer().getUniqueId(), message);
                                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.PLAYER__WARNING_PROTECTED, "{command}", event.getMessage());
                                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.PLAYER__ENTER_CODE);
                                event.setCancelled(true);
                            }
                        } catch (APIException ex) {
                            logger.error(ex.getMessage(), ex);
                            event.setCancelled(true); // Assume event cancellation
                        }
                        break;
                    }
                }
            } else {
                for (String command : cachedConfig.get().getCommands()) {
                    command = command.trim() + " ";

                    if (message.startsWith(command)) {
                        if (isRegister(message) || isCheck(message)) {
                            break; // Skip authentication for 2FA registration/check commands
                        }

                        try {
                            if (!api.isRegistered(event.getPlayer().getUniqueId())) {
                                commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.PLAYER__ERROR_PROTECTED);
                                event.setCancelled(true);
                            } else if (!api.isVerified(event.getPlayer().getUniqueId(), true)) {
                                CollectionProvider.getCommandFrozen().put(event.getPlayer().getUniqueId(), message);
                                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.PLAYER__WARNING_PROTECTED, "{command}", event.getMessage());
                                commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.PLAYER__ENTER_CODE);
                                event.setCancelled(true);
                            }
                        } catch (APIException ex) {
                            logger.error(ex.getMessage(), ex);
                            event.setCancelled(true); // Assume event cancellation
                        }
                        break;
                    }
                }
            }
        }
    }

    private boolean isRegister(String command) {
        String[] parts = command.split("\\s+");

        if (
                (
                        parts[0].equalsIgnoreCase("2faplus")
                                || parts[0].equalsIgnoreCase("tfaplus")
                                || parts[0].equalsIgnoreCase("2fa")
                                || parts[0].equalsIgnoreCase("tfa")
                )
                        &&
                        (
                                parts[1].equalsIgnoreCase("register")
                                        || parts[1].equalsIgnoreCase("create")
                                        || parts[1].equalsIgnoreCase("add")
                        )
        ) {
            return true;
        }
        return false;
    }

    private boolean isCheck(String command) {
        String[] parts = command.split("\\s+");

        if (
                (
                        parts[0].equalsIgnoreCase("2faplus")
                                || parts[0].equalsIgnoreCase("tfaplus")
                                || parts[0].equalsIgnoreCase("2fa")
                                || parts[0].equalsIgnoreCase("tfa")
                )
                        && parts[1].equalsIgnoreCase("check")
        ) {
            return true;
        }
        return false;
    }

    private void verifyCode(AsyncPlayerChatEvent event) {
        String message = event.getMessage().replaceAll("\\s+", "").trim();
        if (message.matches("\\d+")) {
            event.setCancelled(true);

            commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.VERIFY__BEGIN);
            try {
                if (!api.verify(event.getPlayer().getUniqueId(), message)) {
                    commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.VERIFY__FAILURE_INVALID);
                    return;
                }
            } catch (APIException ex) {
                if (ex.isHard()) {
                    logger.error(ex.getMessage(), ex);
                    commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.ERROR__INTERNAL);
                } else {
                    commandManager.getCommandIssuer(event.getPlayer()).sendError(Message.VERIFY__FAILURE_ERROR, "{error}", ex.getMessage());
                }
                return;
            }

            String command = CollectionProvider.getCommandFrozen().remove(event.getPlayer().getUniqueId());
            commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.VERIFY__SUCCESS);
            commandManager.getCommandIssuer(event.getPlayer()).sendInfo(Message.PLAYER__RUNNING_COMMAND);
            if (event.isAsynchronous()) {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().dispatchCommand(event.getPlayer(), command));
            } else {
                Bukkit.getServer().dispatchCommand(event.getPlayer(), command);
            }
        }
    }
}
