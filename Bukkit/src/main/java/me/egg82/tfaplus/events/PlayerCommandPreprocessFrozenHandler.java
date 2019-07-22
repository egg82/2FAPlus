package me.egg82.tfaplus.events;

import co.aikar.commands.CommandManager;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.ConfigUtil;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerCommandPreprocessFrozenHandler implements Consumer<PlayerCommandPreprocessEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TFAAPI api = TFAAPI.getInstance();

    private final CommandManager commandManager;

    public PlayerCommandPreprocessFrozenHandler(CommandManager commandManager) { this.commandManager = commandManager; }

    public void accept(PlayerCommandPreprocessEvent event) {
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
}
