package me.egg82.tfaplus.commands.internal;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.services.lookup.PlayerLookup;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.MapUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterTOTPCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChain<?> chain;
    private final CommandManager commandManager;
    private final CommandIssuer issuer;
    private final String playerName;

    private final TFAAPI api = TFAAPI.getInstance();

    public RegisterTOTPCommand(TaskChain<?> chain, CommandManager commandManager, CommandIssuer issuer, String playerName) {
        this.chain = chain;
        this.commandManager = commandManager;
        this.issuer = issuer;
        this.playerName = playerName;
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
                .<String>asyncCallback((v, f) -> {
                    Optional<Configuration> config = ConfigUtil.getConfig();
                    if (!config.isPresent()) {
                        f.accept(null);
                        return;
                    }

                    try {
                        f.accept(api.registerTOTP(v, config.get().getNode("otp", "digits").getLong()));
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                })
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        issuer.sendError(Message.ERROR__INTERNAL);
                    }
                })
                .syncLast(v -> {
                    if (v == null) {
                        issuer.sendError(Message.REGISTER__FAILURE, "{player}", playerName);
                        return;
                    }

                    Optional<Configuration> config = ConfigUtil.getConfig();
                    if (!config.isPresent()) {
                        return;
                    }

                    Optional<CommandIssuer> playerIssuer = getIssuer(getUuid(playerName));

                    issuer.sendInfo(Message.REGISTER__SUCCESS, "{player}", playerName);
                    if (!playerIssuer.isPresent() || !issuer.isPlayer() || playerIssuer.get().getUniqueId() != issuer.getUniqueId()) {
                        issuer.sendInfo(Message.REGISTER__KEY_OTHER, "{key}", v);
                        if (issuer.isPlayer()) {
                            issuer.sendInfo(Message.REGISTER__QR_CODE);
                            MapUtil.sendTOTPQRCode(issuer.getIssuer(), v, config.get().getNode("otp", "issuer").getString(""), config.get().getNode("otp", "digits").getLong());
                        }
                        issuer.sendInfo(Message.REGISTER__WARNING_PRIVACY);
                    }

                    if (playerIssuer.isPresent()) {
                        playerIssuer.get().sendInfo(Message.REGISTER__KEY, "{key}", v);
                        playerIssuer.get().sendInfo(Message.REGISTER__QR_CODE);
                        MapUtil.sendTOTPQRCode(playerIssuer.get().getIssuer(), v, config.get().getNode("otp", "issuer").getString(""), config.get().getNode("otp", "digits").getLong());
                        playerIssuer.get().sendInfo(Message.REGISTER__WARNING_PRIVACY);
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

    private Optional<CommandIssuer> getIssuer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? Optional.of(commandManager.getCommandIssuer(player)) : Optional.empty();
    }
}
