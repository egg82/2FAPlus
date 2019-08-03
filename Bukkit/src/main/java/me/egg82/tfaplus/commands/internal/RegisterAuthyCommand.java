package me.egg82.tfaplus.commands.internal;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import java.io.IOException;
import java.util.UUID;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.hooks.PlayerAnalyticsHook;
import me.egg82.tfaplus.services.lookup.PlayerLookup;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterAuthyCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChain<?> chain;
    private final CommandIssuer issuer;
    private final String playerName;
    private final String email;
    private final String countryCode;
    private final String phone;

    private final TFAAPI api = TFAAPI.getInstance();

    public RegisterAuthyCommand(TaskChain<?> chain, CommandIssuer issuer, String playerName, String email, String countryCode, String phone) {
        this.chain = chain;
        this.issuer = issuer;
        this.playerName = playerName;
        this.email = email;
        this.countryCode = countryCode;
        this.phone = phone;
    }

    public void run() {
        if (!issuer.<CommandSender>getIssuer().getName().equals(playerName) && !issuer.hasPermission("2faplus.admin")) {
            issuer.sendError(Message.ERROR__NEED_ADMIN_OTHER);
            return;
        }

        issuer.sendInfo(Message.REGISTER__BEGIN, "{player}", playerName);

        chain
                .<UUID>asyncCallback((v, f) -> f.accept(getUuid(playerName)))
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        issuer.sendError(Message.ERROR__NO_UUID, "{player}", playerName);
                    }
                })
                .<Boolean>asyncCallback((v, f) -> {
                    try {
                        if (countryCode != null) {
                            api.registerAuthy(v, email, phone, countryCode);
                        } else {
                            api.registerAuthy(v, email, phone);
                        }
                        try {
                            PlayerAnalyticsHook planHook = ServiceLocator.get(PlayerAnalyticsHook.class);
                            planHook.update(v, playerName);
                        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ignored) { }
                        f.accept(Boolean.TRUE);
                        return;
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                    f.accept(Boolean.FALSE);
                })
                .syncLast(v -> {
                    if (v) {
                        issuer.sendInfo(Message.REGISTER__SUCCESS, "{player}", playerName);
                    } else {
                        issuer.sendError(Message.REGISTER__FAILURE, "{player}", playerName);
                    }
                })
                .execute();
    }

    private UUID getUuid(String name) {
        try {
            return PlayerLookup.get(name).getUUID();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }
}
