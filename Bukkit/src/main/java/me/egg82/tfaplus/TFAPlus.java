package me.egg82.tfaplus;

import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.RegisteredCommand;
import co.aikar.taskchain.BukkitTaskChainFactory;
import co.aikar.taskchain.TaskChainFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import me.egg82.tfaplus.commands.TFAPlusCommand;
import me.egg82.tfaplus.core.SQLFetchResult;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.events.*;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.extended.RabbitMQReceiver;
import me.egg82.tfaplus.extended.RedisSubscriber;
import me.egg82.tfaplus.hooks.PlaceholderAPIHook;
import me.egg82.tfaplus.hooks.PlayerAnalyticsHook;
import me.egg82.tfaplus.hooks.PluginHook;
import me.egg82.tfaplus.services.Redis;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import me.egg82.tfaplus.utils.ConfigurationFileUtil;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.events.BukkitEventSubscriber;
import ninja.egg82.events.BukkitEvents;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import ninja.egg82.updater.SpigotUpdater;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TFAPlus {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ExecutorService workPool = null;

    private TaskChainFactory taskFactory;
    private PaperCommandManager commandManager;

    private List<BukkitEventSubscriber<?>> events = new ArrayList<>();

    private Metrics metrics = null;

    private final Plugin plugin;
    private final boolean isBukkit;

    public TFAPlus(Plugin plugin) {
        isBukkit = Bukkit.getName().equals("Bukkit") || Bukkit.getName().equals("CraftBukkit");
        this.plugin = plugin;
    }

    public void onLoad() {
        if (!Bukkit.getName().equals("Paper") && !Bukkit.getName().equals("PaperSpigot")) {
            log(Level.INFO, ChatColor.AQUA + "====================================");
            log(Level.INFO, ChatColor.YELLOW + "2FAPlus runs better on Paper!");
            log(Level.INFO, ChatColor.YELLOW + "https://whypaper.emc.gs/");
            log(Level.INFO, ChatColor.AQUA + "====================================");
        }

        if (Bukkit.getBukkitVersion().startsWith("1.8") || Bukkit.getBukkitVersion().startsWith("1.8.8")) {
            log(Level.INFO, ChatColor.AQUA + "====================================");
            log(Level.INFO, ChatColor.DARK_RED + "DEAR LORD why are you on 1.8???");
            log(Level.INFO, ChatColor.DARK_RED + "Have you tried ViaVersion or ProtocolSupport lately?");
            log(Level.INFO, ChatColor.AQUA + "====================================");
        }
    }

    public void onEnable() {
        taskFactory = BukkitTaskChainFactory.create(plugin);
        commandManager = new PaperCommandManager(plugin);
        commandManager.enableUnstableAPI("help");

        loadServices();
        loadSQL();
        loadCommands();
        loadEvents();
        loadHooks();
        loadMetrics();

        plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Enabled");

        plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading()
                + ChatColor.YELLOW + "[" + ChatColor.AQUA + "Version " + ChatColor.WHITE + plugin.getDescription().getVersion() + ChatColor.YELLOW +  "] "
                + ChatColor.YELLOW + "[" + ChatColor.WHITE + commandManager.getRegisteredRootCommands().size() + ChatColor.GOLD + " Commands" + ChatColor.YELLOW +  "] "
                + ChatColor.YELLOW + "[" + ChatColor.WHITE + events.size() + ChatColor.BLUE + " Events" + ChatColor.YELLOW +  "]"
        );

        checkUpdate();
    }

    public void onDisable() {
        taskFactory.shutdown(8, TimeUnit.SECONDS);
        commandManager.unregisterCommands();

        for (BukkitEventSubscriber<?> event : events) {
            event.cancel();
        }
        events.clear();

        unloadHooks();
        unloadServices();

        plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "Disabled");
    }

    private void loadServices() {
        ConfigurationFileUtil.reloadConfig(plugin);

        loadServicesExternal();
        ServiceLocator.register(new SpigotUpdater(plugin, 62600));
    }

    public void loadServicesExternal() {
        workPool = Executors.newFixedThreadPool(2, new ThreadFactoryBuilder().setNameFormat("2FAPlus-%d").build());

        Configuration config;
        CachedConfigValues cachedConfig;

        try {
            config = ServiceLocator.get(Configuration.class);
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        workPool.submit(() -> new RedisSubscriber(cachedConfig.getRedisPool(), config.getNode("redis")));
        ServiceLocator.register(new RabbitMQReceiver(cachedConfig.getRabbitConnectionFactory()));
    }

    private void loadSQL() {
        Configuration config;
        CachedConfigValues cachedConfig;

        try {
            config = ServiceLocator.get(Configuration.class);
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (cachedConfig.getSQLType() == SQLType.MySQL) {
            MySQL.createTables(cachedConfig.getSQL(), config.getNode("storage")).thenRun(() ->
                    MySQL.loadInfo(cachedConfig.getSQL(), config.getNode("storage")).thenAccept(v -> {
                        Redis.updateFromQueue(v, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"));
                        updateSQL();
                    })
            );
        } else if (cachedConfig.getSQLType() == SQLType.SQLite) {
            SQLite.createTables(cachedConfig.getSQL(), config.getNode("storage")).thenRun(() ->
                    SQLite.loadInfo(cachedConfig.getSQL(), config.getNode("storage")).thenAccept(v -> {
                        Redis.updateFromQueue(v, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"));
                        updateSQL();
                    })
            );
        }
    }

    public void loadSQLExternal() {
        Configuration config;
        CachedConfigValues cachedConfig;

        try {
            config = ServiceLocator.get(Configuration.class);
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (cachedConfig.getSQLType() == SQLType.MySQL) {
            MySQL.createTables(cachedConfig.getSQL(), config.getNode("storage")).thenRun(() ->
                    MySQL.loadInfo(cachedConfig.getSQL(), config.getNode("storage")).thenAccept(v -> {
                        Redis.updateFromQueue(v, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"));
                    })
            );
        } else if (cachedConfig.getSQLType() == SQLType.SQLite) {
            SQLite.createTables(cachedConfig.getSQL(), config.getNode("storage")).thenRun(() ->
                    SQLite.loadInfo(cachedConfig.getSQL(), config.getNode("storage")).thenAccept(v -> {
                        Redis.updateFromQueue(v, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis"));
                    })
            );
        }
    }

    private void updateSQL() {
        workPool.submit(() -> {
            try {
                Thread.sleep(10L * 1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            Configuration config;
            CachedConfigValues cachedConfig;

            try {
                config = ServiceLocator.get(Configuration.class);
                cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
                logger.error(ex.getMessage(), ex);
                return;
            }

            SQLFetchResult result = null;

            try {
                if (cachedConfig.getSQLType() == SQLType.MySQL) {
                    result = MySQL.fetchQueue(cachedConfig.getSQL(), config.getNode("storage")).get();
                }

                if (result != null) {
                    Redis.updateFromQueue(result, cachedConfig.getIPTime(), cachedConfig.getRedisPool(), config.getNode("redis")).get();
                }
            } catch (ExecutionException ex) {
                logger.error(ex.getMessage(), ex);
            } catch (InterruptedException ex) {
                logger.error(ex.getMessage(), ex);
                Thread.currentThread().interrupt();
            }

            updateSQL();
        });
    }

    private void loadCommands() {
        commandManager.getCommandCompletions().registerCompletion("player", c -> {
            String lower = c.getInput().toLowerCase();
            Set<String> players = new LinkedHashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (lower.isEmpty() || p.getName().toLowerCase().startsWith(lower)) {
                    Player player = c.getPlayer();
                    if (c.getSender().isOp() || (player != null && player.canSee(p) && !isVanished(p))) {
                        players.add(p.getName());
                    }
                }
            }
            return ImmutableList.copyOf(players);
        });

        commandManager.getCommandCompletions().registerCompletion("subcommand", c -> {
            String lower = c.getInput().toLowerCase();
            Set<String> commands = new LinkedHashSet<>();
            SetMultimap<String, RegisteredCommand> subcommands = commandManager.getRootCommand("2faplus").getSubCommands();
            for (Map.Entry<String, RegisteredCommand> kvp : subcommands.entries()) {
                if (!kvp.getValue().isPrivate() && (lower.isEmpty() || kvp.getKey().toLowerCase().startsWith(lower)) && kvp.getValue().getCommand().indexOf(' ') == -1) {
                    commands.add(kvp.getValue().getCommand());
                }
            }
            return ImmutableList.copyOf(commands);
        });

        commandManager.registerCommand(new TFAPlusCommand(this, plugin, taskFactory));
    }

    private void loadEvents() {
        events.add(BukkitEvents.subscribe(AsyncPlayerChatEvent.class, EventPriority.HIGHEST).handler(e -> new AsyncPlayerChatFrozenHandler(plugin).accept(e)));
        events.add(BukkitEvents.subscribe(PlayerCommandPreprocessEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerCommandPreprocessFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerInteractEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerInteractFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(EntityDamageByEntityEvent.class, EventPriority.HIGHEST).handler(e -> new EntityDamageByEntityFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(InventoryClickEvent.class, EventPriority.HIGHEST).handler(e -> new InventoryClickFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(InventoryDragEvent.class, EventPriority.HIGHEST).handler(e -> new InventoryDragFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(InventoryMoveItemEvent.class, EventPriority.HIGHEST).handler(e -> new InventoryMoveItemFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerPickupItemEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerPickupItemFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerPickupArrowEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerPickupArrowFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerDropItemEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerDropItemFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(BlockPlaceEvent.class, EventPriority.HIGHEST).handler(e -> new BlockPlaceFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(BlockBreakEvent.class, EventPriority.HIGHEST).handler(e -> new BlockBreakFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerMoveEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerMoveFrozenHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerTeleportEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerTeleportFrozenHandler().accept(e)));

        events.add(BukkitEvents.subscribe(AsyncPlayerPreLoginEvent.class, EventPriority.HIGH).handler(e -> new AsyncPlayerPreLoginCacheHandler().accept(e)));
        events.add(BukkitEvents.subscribe(PlayerLoginEvent.class, EventPriority.HIGHEST).handler(e -> new PlayerLoginCheckHandler(plugin).accept(e)));
        events.add(BukkitEvents.subscribe(PlayerLoginEvent.class, EventPriority.LOW).handler(e -> new PlayerLoginUpdateNotifyHandler(plugin).accept(e)));
        events.add(BukkitEvents.subscribe(PlayerQuitEvent.class, EventPriority.LOW).handler(e -> new PlayerQuitFrozenHandler().accept(e)));
    }

    private void loadHooks() {
        PluginManager manager = plugin.getServer().getPluginManager();

        if (manager.getPlugin("Plan") != null) {
            plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Enabling support for Plan.");
            ServiceLocator.register(new PlayerAnalyticsHook());
        } else {
            plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Plan was not found. Personal analytics support has been disabled.");
        }

        if (manager.getPlugin("PlaceholderAPI") != null) {
            plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Enabling support for PlaceholderAPI.");
            ServiceLocator.register(new PlaceholderAPIHook());
        } else {
            plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "PlaceholderAPI was not found. Skipping support for placeholders.");
        }
    }

    private void loadMetrics() {
        metrics = new Metrics(plugin);
        metrics.addCustomChart(new Metrics.SimplePie("sql", () -> {
            Configuration config;
            try {
                config = ServiceLocator.get(Configuration.class);
            } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
                logger.error(ex.getMessage(), ex);
                return null;
            }

            if (!config.getNode("stats", "usage").getBoolean(true)) {
                return null;
            }

            return config.getNode("storage", "method").getString("sqlite");
        }));
        metrics.addCustomChart(new Metrics.SimplePie("redis", () -> {
            Configuration config;
            try {
                config = ServiceLocator.get(Configuration.class);
            } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
                logger.error(ex.getMessage(), ex);
                return null;
            }

            if (!config.getNode("stats", "usage").getBoolean(true)) {
                return null;
            }

            return config.getNode("redis", "enabled").getBoolean(false) ? "yes" : "no";
        }));
        metrics.addCustomChart(new Metrics.SimplePie("rabbitmq", () -> {
            Configuration config;
            try {
                config = ServiceLocator.get(Configuration.class);
            } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
                logger.error(ex.getMessage(), ex);
                return null;
            }

            if (!config.getNode("stats", "usage").getBoolean(true)) {
                return null;
            }

            return config.getNode("rabbitmq", "enabled").getBoolean(false) ? "yes" : "no";
        }));
    }

    private void checkUpdate() {
        Configuration config;
        SpigotUpdater updater;
        try {
            config = ServiceLocator.get(Configuration.class);
            updater = ServiceLocator.get(SpigotUpdater.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (!config.getNode("update", "check").getBoolean(true)) {
            return;
        }

        updater.isUpdateAvailable().thenAccept(v -> {
            if (!v) {
                return;
            }

            if (config.getNode("update", "notify").getBoolean(true)) {
                try {
                    plugin.getServer().getConsoleSender().sendMessage(LogUtil.getHeading() + ChatColor.AQUA + " has an " + ChatColor.GREEN + "update" + ChatColor.AQUA + " available! New version: " + ChatColor.YELLOW + updater.getLatestVersion().get());
                } catch (ExecutionException ex) {
                    logger.error(ex.getMessage(), ex);
                } catch (InterruptedException ex) {
                    logger.error(ex.getMessage(), ex);
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void unloadHooks() {
        Optional<? extends PluginHook> plan;
        try {
            plan = ServiceLocator.getOptional(PlayerAnalyticsHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            plan = Optional.empty();
        }
        plan.ifPresent(v -> v.cancel());

        Optional<? extends PluginHook> placeholderapi;
        try {
            placeholderapi = ServiceLocator.getOptional(PlaceholderAPIHook.class);
        } catch (InstantiationException | IllegalAccessException ex) {
            logger.error(ex.getMessage(), ex);
            placeholderapi = Optional.empty();
        }
        placeholderapi.ifPresent(v -> v.cancel());
    }

    public void unloadServices() {
        CachedConfigValues cachedConfig;
        RabbitMQReceiver rabbitReceiver;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            rabbitReceiver = ServiceLocator.get(RabbitMQReceiver.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        cachedConfig.getSQL().close();

        if (cachedConfig.getRedisPool() != null) {
            cachedConfig.getRedisPool().close();
        }

        try {
            rabbitReceiver.close();
        } catch (IOException | TimeoutException ignored) {}

        if (!workPool.isShutdown()) {
            workPool.shutdown();
            try {
                if (!workPool.awaitTermination(8L, TimeUnit.SECONDS)) {
                    workPool.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                workPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        return false;
    }

    private void log(Level level, String message) {
        plugin.getServer().getLogger().log(level, (isBukkit) ? ChatColor.stripColor(message) : message);
    }
}
