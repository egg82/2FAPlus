package me.egg82.tfaplus.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import co.aikar.taskchain.TaskChainFactory;
import java.util.ArrayList;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.tuples.longs.LongObjectPair;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandAlias("hotp")
public class HOTPCommand extends BaseCommand {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChainFactory taskFactory;

    private final TFAAPI api = TFAAPI.getInstance();

    public HOTPCommand(TaskChainFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Subcommand("seek|reset|resync|resynchronize|sync|synchronize")
    @Description("Re-synchronizes your HOTP counter using the next few HOTP codes provided by your client.")
    public void onSeek(Player sender) {
        taskFactory.newChain()
                .<Boolean>asyncCallback((v, f) -> {
                    try {
                        f.accept(api.isRegistered(sender.getUniqueId()));
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                })
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        sender.sendMessage(LogUtil.getHeading() + LogUtil.getHeading() + ChatColor.YELLOW + "Internal error");
                    }
                })
                .syncLast(v -> {
                    if (!v) {
                        sender.sendMessage(LogUtil.getHeading() + LogUtil.getHeading() + ChatColor.YELLOW + "You must have 2FA enabled for this command to have any affect.");
                        return;
                    }

                    long numCodes = getRandom(3L, 4L);

                    sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Please provide the next " + ChatColor.WHITE + numCodes + ChatColor.YELLOW + " codes from your client.");
                    CollectionProvider.getHOTPFrozen().put(sender.getUniqueId(), new LongObjectPair<>(numCodes, new ArrayList<>()));
                })
                .execute();
    }

    @CatchUnknown @Default
    @CommandCompletion("@hotp-subcommand")
    public void onDefault(CommandSender sender, String[] args) {
        Bukkit.getServer().dispatchCommand(sender, "hotp help");
    }

    @HelpCommand
    @Syntax("[command]")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    private long getRandom(long min, long max) {
        long num;
        max++;

        do {
            num = (long) Math.floor(Math.random() * (max - min) + min);
        } while (num > max - 1L);

        return num;
    }
}
