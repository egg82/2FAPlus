package me.egg82.tfaplus.commands.internal;

import co.aikar.commands.CommandIssuer;
import co.aikar.taskchain.TaskChain;
import me.egg82.tfaplus.enums.Message;
import me.egg82.tfaplus.utils.ConfigurationFileUtil;
import me.egg82.tfaplus.utils.ServiceUtil;
import org.bukkit.plugin.Plugin;

public class ReloadCommand implements Runnable {
    private final Plugin plugin;
    private final TaskChain<?> chain;
    private final CommandIssuer issuer;

    public ReloadCommand(Plugin plugin, TaskChain<?> chain, CommandIssuer issuer) {
        this.plugin = plugin;
        this.chain = chain;
        this.issuer = issuer;
    }

    public void run() {
        issuer.sendInfo(Message.RELOAD__BEGIN);

        chain
                .async(ServiceUtil::unregisterWorkPool)
                .async(ServiceUtil::unregisterRedis)
                .async(ServiceUtil::unregisterRabbit)
                .async(ServiceUtil::unregisterSQL)
                .async(() -> ConfigurationFileUtil.reloadConfig(plugin))
                .async(ServiceUtil::registerWorkPool)
                .async(ServiceUtil::registerRedis)
                .async(ServiceUtil::registerRabbit)
                .async(ServiceUtil::registerSQL)
                .sync(() -> issuer.sendInfo(Message.RELOAD__END))
                .execute();
    }
}
