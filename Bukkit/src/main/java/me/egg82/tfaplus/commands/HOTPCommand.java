package me.egg82.tfaplus.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChainFactory;
import java.util.ArrayList;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.tuples.longs.LongObjectPair;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("hotp")
public class HOTPCommand extends BaseCommand {
    private final TaskChainFactory taskFactory;

    public HOTPCommand(TaskChainFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Subcommand("seek|reset|resync|resynchronize|sync|synchronize")
    @Description("Re-synchronizes your HOTP counter using the next few HOTP codes provided by your client.")
    public void onSeek(Player sender) {
        long numCodes = getRandom(2L, 4L);

        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Please provide the next " + ChatColor.WHITE + numCodes + ChatColor.YELLOW + " codes from your client.");
        CollectionProvider.getHOTPFrozen().put(sender.getUniqueId(), new LongObjectPair<>(numCodes, new ArrayList<>()));
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
