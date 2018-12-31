package me.egg82.tfaplus.utils;

import com.authy.AuthyApiClient;
import com.google.common.reflect.TypeToken;
import com.rabbitmq.client.ConnectionFactory;
import com.zaxxer.hikari.HikariConfig;
import java.io.*;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.core.FreezeConfigContainer;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.extended.RabbitMQReceiver;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import redis.clients.jedis.JedisPool;

public class ConfigurationFileUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationFileUtil.class);

    private ConfigurationFileUtil() {}

    public static void reloadConfig(Plugin plugin) {
        Configuration config;
        try {
            config = getConfig(plugin, "config.yml", new File(plugin.getDataFolder(), "config.yml"));
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        boolean debug = config.getNode("debug").getBoolean(false);

        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Debug " + ChatColor.WHITE + "enabled");
        }

        String ipTime = config.getNode("2fa", "ip-lifetime").getString("30days");
        Optional<Long> ipTimeLong = TimeUtil.getTime(ipTime);
        Optional<TimeUnit> ipTimeUnit = TimeUtil.getUnit(ipTime);
        if (!ipTimeLong.isPresent()) {
            logger.warn("2fa.ip-lifetime is not a valid time pattern. Using default value.");
            ipTimeLong = Optional.of(30L);
            ipTimeUnit = Optional.of(TimeUnit.DAYS);
        }
        if (!ipTimeUnit.isPresent()) {
            logger.warn("2fa.ip-lifetime is not a valid time pattern. Using default value.");
            ipTimeLong = Optional.of(30L);
            ipTimeUnit = Optional.of(TimeUnit.DAYS);
        }

        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "IP lifetime: " + ChatColor.WHITE + ipTimeUnit.get().toMillis(ipTimeLong.get()) + " millis");
        }

        String verificationTime = config.getNode("2fa", "verification-lifetime").getString("3minutes");
        Optional<Long> verificationTimeLong = TimeUtil.getTime(verificationTime);
        Optional<TimeUnit> verificationTimeUnit = TimeUtil.getUnit(verificationTime);
        if (!verificationTimeLong.isPresent()) {
            logger.warn("2fa.verification-lifetime is not a valid time pattern. Using default value.");
            verificationTimeLong = Optional.of(3L);
            verificationTimeUnit = Optional.of(TimeUnit.MINUTES);
        }
        if (!verificationTimeUnit.isPresent()) {
            logger.warn("2fa.verification-lifetime is not a valid time pattern. Using default value.");
            verificationTimeLong = Optional.of(3L);
            verificationTimeUnit = Optional.of(TimeUnit.MINUTES);
        }

        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Verification lifetime: " + ChatColor.WHITE + verificationTimeUnit.get().toMillis(verificationTimeLong.get()) + " millis");
        }

        Set<String> commands;
        try {
            commands = new HashSet<>(config.getNode("2fa", "command-list").getList(TypeToken.of(String.class)));
        } catch (ObjectMappingException ex) {
            logger.error(ex.getMessage(), ex);
            commands = new HashSet<>();
        }

        Set<String> addedCommands = new HashSet<>();
        for (Iterator<String> i = commands.iterator(); i.hasNext();) {
            String command = i.next();
            if (command.charAt(0) == '/') {
                i.remove();
                addedCommands.add(command.substring(1));
            }
            String lower = command.toLowerCase();
            if (lower.startsWith("hotp ") || command.equals("hotp") || lower.startsWith("2faplus:hotp")) {
                i.remove();
            }
        }
        commands.addAll(addedCommands);

        if (debug) {
            for (String command : commands) {
                logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Protecting command: " + ChatColor.WHITE + "/" + command);
            }
        }

        boolean forceAuth = config.getNode("2fa", "force-auth").getBoolean(true);
        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Force-authentication enabled: " + ChatColor.WHITE + forceAuth);
        }

        long maxAttempts = config.getNode("2fa", "max-attempts").getLong(3L);
        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Max auth attempts: " + ChatColor.WHITE + maxAttempts);
        }

        FreezeConfigContainer freeze = new FreezeConfigContainer(
                config.getNode("2fa", "freeze", "command").getBoolean(true),
                config.getNode("2fa", "freeze", "chat").getBoolean(true),
                config.getNode("2fa", "freeze", "interact").getBoolean(true),
                config.getNode("2fa", "freeze", "attack").getBoolean(true),
                config.getNode("2fa", "freeze", "inventory").getBoolean(true),
                config.getNode("2fa", "freeze", "drops").getBoolean(true),
                config.getNode("2fa", "freeze", "blocks").getBoolean(true),
                config.getNode("2fa", "freeze", "move").getBoolean(true)
        );

        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing commands: " + ChatColor.WHITE + freeze.getCommand());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing chat: " + ChatColor.WHITE + freeze.getChat());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing interact: " + ChatColor.WHITE + freeze.getInteract());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing attacks: " + ChatColor.WHITE + freeze.getAttack());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing inventory: " + ChatColor.WHITE + freeze.getInventory());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing drops/pickups: " + ChatColor.WHITE + freeze.getDrops());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing block place/break: " + ChatColor.WHITE + freeze.getBlocks());
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Freezing movement: " + ChatColor.WHITE + freeze.getMove());
        }

        Set<String> ignored;
        try {
            ignored = new HashSet<>(config.getNode("ignore").getList(TypeToken.of(String.class)));
        } catch (ObjectMappingException ex) {
            logger.error(ex.getMessage(), ex);
            ignored = new HashSet<>();
        }

        if (debug) {
            for (String value : ignored) {
                logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Ignoring: " + ChatColor.WHITE + value);
            }
        }

        try {
            destroyServices(ServiceLocator.getOptional(CachedConfigValues.class), ServiceLocator.getOptional(RabbitMQReceiver.class));
        } catch (InstantiationException | IllegalAccessException | IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        CachedConfigValues cachedValues = CachedConfigValues.builder()
                .debug(debug)
                .ipTime(ipTimeLong.get(), ipTimeUnit.get())
                .verificationTime(verificationTimeLong.get(), verificationTimeUnit.get())
                .commands(commands)
                .forceAuth(forceAuth)
                .maxAttempts(maxAttempts)
                .freeze(freeze)
                .ignored(ignored)
                .redisPool(getRedisPool(config.getNode("redis")))
                .rabbitConnectionFactory(getRabbitConnectionFactory(config.getNode("rabbitmq")))
                .sql(getSQL(plugin, config.getNode("storage")))
                .sqlType(config.getNode("storage", "method").getString("sqlite"))
                .authy(getAuthy(config.getNode("authy", "key").getString(""), debug))
                .build();

        ServiceLocator.register(config);
        ServiceLocator.register(cachedValues);

        if (debug) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Using Redis: " + ChatColor.WHITE + (cachedValues.getRedisPool() != null));
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Using RabbitMQ: " + ChatColor.WHITE + (cachedValues.getRabbitConnectionFactory() != null));
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "SQL type: " + ChatColor.WHITE + cachedValues.getSQLType().name());
        }
    }

    public static Configuration getConfig(Plugin plugin, String resourcePath, File fileOnDisk) throws IOException {
        File parentDir = fileOnDisk.getParentFile();
        if (parentDir.exists() && !parentDir.isDirectory()) {
            Files.delete(parentDir.toPath());
        }
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Could not create parent directory structure.");
            }
        }
        if (fileOnDisk.exists() && fileOnDisk.isDirectory()) {
            Files.delete(fileOnDisk.toPath());
        }

        if (!fileOnDisk.exists()) {
            try (InputStreamReader reader = new InputStreamReader(plugin.getResource(resourcePath));
                 BufferedReader in = new BufferedReader(reader);
                 FileWriter writer = new FileWriter(fileOnDisk);
                 BufferedWriter out = new BufferedWriter(writer)) {
                String line;
                while ((line = in.readLine()) != null) {
                    out.write(line + System.lineSeparator());
                }
            }
        }

        ConfigurationLoader<ConfigurationNode> loader = YAMLConfigurationLoader.builder().setFlowStyle(DumperOptions.FlowStyle.BLOCK).setIndent(2).setFile(fileOnDisk).build();
        ConfigurationNode root = loader.load(ConfigurationOptions.defaults().setHeader("Comments are gone because update :(. Click here for new config + comments: https://www.spigotmc.org/resources/2faplus.62600/"));
        Configuration config = new Configuration(root);
        ConfigurationVersionUtil.conformVersion(loader, config, fileOnDisk);

        return config;
    }

    private static void destroyServices(Optional<CachedConfigValues> cachedConfigValues, Optional<RabbitMQReceiver> rabbitReceiver) throws IOException, TimeoutException {
        if (!cachedConfigValues.isPresent()) {
            return;
        }

        cachedConfigValues.get().getSQL().close();

        if (cachedConfigValues.get().getRedisPool() != null) {
            cachedConfigValues.get().getRedisPool().close();
        }

        if (rabbitReceiver.isPresent()) {
            rabbitReceiver.get().close();
        }
    }

    private static Optional<AuthyApiClient> getAuthy(String key, boolean debug) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new AuthyApiClient(key, "https://api.authy.com/", debug));
    }

    private static SQL getSQL(Plugin plugin, ConfigurationNode storageConfigNode) {
        SQLType type = SQLType.getByName(storageConfigNode.getNode("method").getString("sqlite"));
        if (type == SQLType.UNKNOWN) {
            logger.warn("storage.method is an unknown value. Using default value.");
            type = SQLType.SQLite;
        }

        HikariConfig hikariConfig = new HikariConfig();
        if (type == SQLType.MySQL) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + storageConfigNode.getNode("data", "address").getString("127.0.0.1:3306") + "/" + storageConfigNode.getNode("data", "database").getString("avpn"));
            hikariConfig.setConnectionTestQuery("SELECT 1;");
        } else if (type == SQLType.SQLite) {
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + new File(plugin.getDataFolder(), storageConfigNode.getNode("data", "database").getString("avpn") + ".db").getAbsolutePath());
            hikariConfig.setConnectionTestQuery("SELECT 1;");
        }
        hikariConfig.setUsername(storageConfigNode.getNode("data", "username").getString(""));
        hikariConfig.setPassword(storageConfigNode.getNode("data", "password").getString(""));
        hikariConfig.setMaximumPoolSize(storageConfigNode.getNode("settings", "max-pool-size").getInt(2));
        hikariConfig.setMinimumIdle(storageConfigNode.getNode("settings", "min-idle").getInt(2));
        hikariConfig.setMaxLifetime(storageConfigNode.getNode("settings", "max-lifetime").getLong(1800000L));
        hikariConfig.setConnectionTimeout(storageConfigNode.getNode("settings", "timeout").getLong(5000L));
        hikariConfig.addDataSourceProperty("useUnicode", String.valueOf(storageConfigNode.getNode("settings", "properties", "unicode").getBoolean(true)));
        hikariConfig.addDataSourceProperty("characterEncoding", storageConfigNode.getNode("settings", "properties", "encoding").getString("utf8"));
        hikariConfig.setAutoCommit(true);

        // Optimizations
        if (type == SQLType.MySQL) {
            hikariConfig.addDataSourceProperty("useSSL", String.valueOf(storageConfigNode.getNode("data", "ssl").getBoolean(false)));
            // http://assets.en.oreilly.com/1/event/21/Connector_J%20Performance%20Gems%20Presentation.pdf
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("useLocalTransactionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            hikariConfig.addDataSourceProperty("useUnbufferedIO", "false");
            hikariConfig.addDataSourceProperty("useReadAheadInput", "false");
            // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        }

        return new SQL(hikariConfig);
    }

    private static JedisPool getRedisPool(ConfigurationNode redisConfigNode) {
        if (!redisConfigNode.getNode("enabled").getBoolean(false)) {
            return null;
        }

        String address = redisConfigNode.getNode("address").getString("127.0.0.1:6379");
        int portIndex = address.indexOf(':');
        int port;
        if (portIndex > -1) {
            port = Integer.parseInt(address.substring(portIndex + 1));
            address = address.substring(0, portIndex);
        } else {
            logger.warn("redis.address port is an unknown value. Using default value.");
            port = 6379;
        }

        return new JedisPool(address, port);
    }

    private static ConnectionFactory getRabbitConnectionFactory(ConfigurationNode rabbitConfigNode) {
        if (!rabbitConfigNode.getNode("enabled").getBoolean(false)) {
            return null;
        }

        String address = rabbitConfigNode.getNode("address").getString("127.0.0.1:5672");
        int portIndex = address.indexOf(':');
        int port;
        if (portIndex > -1) {
            port = Integer.parseInt(address.substring(portIndex + 1));
            address = address.substring(0, portIndex);
        } else {
            logger.warn("rabbitmq.address port is an unknown value. Using default value.");
            port = 5672;
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(address);
        factory.setPort(port);
        factory.setVirtualHost("/");
        factory.setUsername(rabbitConfigNode.getNode("username").getString("guest"));
        factory.setPassword(rabbitConfigNode.getNode("password").getString("guest"));

        return factory;
    }
}
