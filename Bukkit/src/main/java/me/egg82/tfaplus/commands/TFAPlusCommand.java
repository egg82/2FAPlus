package me.egg82.tfaplus.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChainFactory;
import me.egg82.tfaplus.commands.internal.*;
import me.egg82.tfaplus.enums.Message;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

@CommandAlias("2faplus|tfaplus|2fa|tfa")
public class TFAPlusCommand extends BaseCommand {
    private final Plugin plugin;
    private final TaskChainFactory taskFactory;

    public TFAPlusCommand(Plugin plugin, TaskChainFactory taskFactory) {
        this.plugin = plugin;
        this.taskFactory = taskFactory;
    }

    @Subcommand("reload")
    @CommandPermission("2faplus.admin")
    @Description("{@@description.reload}")
    public void onReload(CommandIssuer issuer) {
        new ReloadCommand(plugin, taskFactory.newChain(), issuer).run();
    }

    @Subcommand("register|create|add authy")
    @CommandPermission("2faplus.use")
    @Description("{@@description.register_authy}")
    @Syntax("<player> <email> [phone-country-code] <phone-number>")
    @CommandCompletion("@player")
    public void onRegisterAuthy(CommandIssuer issuer, String playerName, String email, String countryCode, String phone) {
        new RegisterAuthyCommand(taskFactory.newChain(), issuer, playerName, email, countryCode, phone).run();
    }

    @Subcommand("register|create|add totp")
    @CommandPermission("2faplus.use")
    @Description("{@@description.register_totp}")
    @Syntax("<player>")
    @CommandCompletion("@player")
    public void onRegisterTOTP(CommandIssuer issuer, String playerName) {
        new RegisterTOTPCommand(taskFactory.newChain(), issuer, playerName).run();
    }

    @Subcommand("register|create|add hotp")
    @CommandPermission("2faplus.use")
    @Description("{@@description.register_hotp}")
    @Syntax("<player>")
    @CommandCompletion("@player")
    public void onRegisterHOTP(CommandIssuer issuer, String playerName) {
        new RegisterHOTPCommand(taskFactory.newChain(), issuer, playerName).run();
    }

    @Subcommand("delete|remove")
    @CommandPermission("2faplus.use")
    @Description("{@@description.delete}")
    @Syntax("<player>")
    @CommandCompletion("@player")
    public void onDelete(CommandSender sender, String playerName) {
        new DeleteCommand(taskFactory.newChain(), sender, playerName).run();
    }

    @Subcommand("check")
    @CommandPermission("2faplus.use")
    @Description("{@@description.check}")
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
    public void onHelp(CommandIssuer issuer, CommandHelp help) {
        help.showHelp();
        issuer.sendInfo(Message.DESCRIPTION__MAIN_HELP);
    }
}
