package me.egg82.tfaplus.commands.internal;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import java.io.IOException;
import java.util.UUID;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.services.lookup.PlayerLookup;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChain<?> chain;
    private final CommandIssuer issuer;
    private final String playerName;

    private final TFAAPI api = TFAAPI.getInstance();

    public DeleteCommand(TaskChain<?> chain, CommandIssuer issuer, String playerName) {
        this.chain = chain;
        this.issuer = issuer;
        this.playerName = playerName;
    }

    public void run() {
        if (!issuer.<CommandSender>getIssuer().getName().equals(playerName) && !issuer.hasPermission("2faplus.admin")) {
            issuer.sendError(Message.ERROR__NEED_ADMIN_OTHER);
            return;
        }

        issuer.sendInfo(Message.DELETE__BEGIN, "{player}", playerName);

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
                        api.delete(v);
                        f.accept(Boolean.TRUE);
                        return;
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                    f.accept(Boolean.FALSE);
                })
                .abortIf(v -> !v, new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        issuer.sendError(Message.ERROR__INTERNAL);
                    }
                })
                .syncLast(v -> {
                    if (v) {
                        issuer.sendInfo(Message.DELETE__SUCCESS, "{player}", playerName);
                    } else {
                        issuer.sendError(Message.DELETE__FAILURE, "{player}", playerName);
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
