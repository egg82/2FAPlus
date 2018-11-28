package me.egg82.tfaplus.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChainFactory;
import me.egg82.tfaplus.TFAPlus;
import me.egg82.tfaplus.commands.internal.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

@CommandAlias("2faplus|tfaplus|2fa|tfa")
public class TFAPlusCommand extends BaseCommand {
    private final TFAPlus concrete;
    private final Plugin plugin;
    private final TaskChainFactory taskFactory;

    public TFAPlusCommand(TFAPlus concrete, Plugin plugin, TaskChainFactory taskFactory) {
        this.concrete = concrete;
        this.plugin = plugin;
        this.taskFactory = taskFactory;
    }

    @Subcommand("reload")
    @CommandPermission("2faplus.admin")
    @Description("Reloads the plugin.")
    public void onReload(CommandSender sender) {
        new ReloadCommand(concrete, plugin, taskFactory.newChain(), sender).run();
    }

    @Subcommand("register|create|add authy")
    @CommandPermission("2faplus.admin")
    @Description("Registers a player in the Authy 2FA system. Valid country codes can be found at https://countrycode.org/")
    @Syntax("<player> <email> [phone-country-code] <phone-number>")
    @CommandCompletion("@player")
    public void onRegisterAuthy(CommandSender sender, String playerName, String email, String countryCode, String phone) {
        new RegisterAuthyCommand(taskFactory.newChain(), sender, playerName, email, countryCode, phone).run();
    }

    @Subcommand("register|create|add totp")
    @CommandPermission("2faplus.admin")
    @Description("Registers a player in the TOTP 2FA system. For Google, Microsoft, LastPass, etc.")
    @Syntax("<player>")
    @CommandCompletion("@player")
    public void onRegisterTOTP(CommandSender sender, String playerName) {
        new RegisterTOTPCommand(taskFactory.newChain(), sender, playerName).run();
    }

    @Subcommand("remove|delete")
    @CommandPermission("2faplus.admin")
    @Description("Removes a player in the 2FA system.")
    @Syntax("<player>")
    @CommandCompletion("@player")
    public void onDelete(CommandSender sender, String playerName) {
        new DeleteCommand(taskFactory.newChain(), sender, playerName).run();
    }

    @Subcommand("check")
    @CommandPermission("2faplus.admin")
    @Description("Checks the player's registration status in the 2FA system.")
    @Syntax("<player>")
    @CommandCompletion("@player")
    public void onCheck(CommandSender sender, String playerName) {
        new CheckCommand(taskFactory.newChain(), sender, playerName).run();
    }

    @CatchUnknown @Default
    @CommandCompletion("@subcommand")
    public void onDefault(CommandSender sender, String[] args) {
        Bukkit.getServer().dispatchCommand(sender, "2faplus help");
    }

    @HelpCommand
    @Syntax("[command]")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }
}
