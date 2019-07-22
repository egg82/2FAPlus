package me.egg82.tfaplus.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import co.aikar.taskchain.TaskChainFactory;
import java.util.ArrayList;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.services.CollectionProvider;
import ninja.egg82.tuples.longs.LongObjectPair;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
    @Description("{@@description.seek}")
    public void onSeek(CommandIssuer issuer) {
        if (!issuer.isPlayer()) {
            issuer.sendError(Message.ERROR__PLAYER_ONLY);
            return;
        }

        taskFactory.newChain()
                .<Boolean>asyncCallback((v, f) -> {
                    try {
                        f.accept(api.isRegistered(issuer.getUniqueId()));
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
                    if (!v) {
                        issuer.sendError(Message.SEEK__2FA_NOT_ENABLED);
                        return;
                    }

                    long numCodes = getRandom(3L, 4L);

                    issuer.sendInfo(Message.SEEK__NEXT_CODES, "{codes}", String.valueOf(numCodes));
                    CollectionProvider.getHOTPFrozen().put(issuer.getUniqueId(), new LongObjectPair<>(numCodes, new ArrayList<>()));
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
