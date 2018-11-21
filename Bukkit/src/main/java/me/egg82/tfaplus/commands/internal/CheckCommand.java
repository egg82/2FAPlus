package me.egg82.tfaplus.commands.internal;

import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import java.io.IOException;
import java.util.UUID;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.services.lookup.PlayerLookup;
import me.egg82.tfaplus.utils.LogUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChain<?> chain;
    private final CommandSender sender;
    private final String playerName;

    private final TFAAPI api = TFAAPI.getInstance();

    public CheckCommand(TaskChain<?> chain, CommandSender sender, String playerName) {
        this.chain = chain;
        this.sender = sender;
        this.playerName = playerName;
    }

    public void run() {
        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Checking " + ChatColor.WHITE + playerName + ChatColor.YELLOW + ", please wait..");

        chain
                .<UUID>asyncCallback((v, f) -> f.accept(getUuid(playerName)))
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        sender.sendMessage(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + playerName + ChatColor.DARK_RED + " (rate-limited?)");
                    }
                })
                .<Boolean>asyncCallback((v, f) -> f.accept(api.isRegistered(v)))
                .syncLast(v -> sender.sendMessage(LogUtil.getHeading() + (v ? ChatColor.WHITE + playerName + ChatColor.GREEN + " is registered." : ChatColor.WHITE + playerName + ChatColor.RED + " is not registered.")))
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
