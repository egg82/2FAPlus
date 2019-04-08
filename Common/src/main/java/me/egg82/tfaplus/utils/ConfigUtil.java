package me.egg82.tfaplus.utils;

import com.authy.AuthyApiClient;
import com.authy.api.Tokens;
import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.ConnectionFactory;
import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.core.FreezeConfigContainer;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import redis.clients.jedis.JedisPool;

import java.util.Optional;

public class ConfigUtil {
    /**
     * Grabs the config instance from ServiceLocator
     * @return instance of the Configuration class, may return null
     */
    public static Configuration getConfig()
    {
        Configuration config;

        try {
            config = ServiceLocator.get(Configuration.class);
            return config;
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            TFAAPI.getLogger().error(ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * Grabs the cached config instance from ServiceLocator
     * @return instance of the CachedConfigValues class, may return null
     */
    public static CachedConfigValues getCachedConfig()
    {
        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            return cachedConfig;
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            TFAAPI.getLogger().error(ex.getMessage(), ex);
        }

        return null;
    }

    public static SQL getSQL()
    {
        return getCachedConfig().getSQL();
    }

    public static Tokens getTokens()
    {
        return getCachedConfig().getAuthy().get().getTokens();
    }

    public static boolean isDebugging() {
        return getCachedConfig().getDebug();
    }

    public static long getIPTime() {
        return getCachedConfig().getIPTime();
    }

    public static long getVerificationTime() {
        return getCachedConfig().getVerificationTime();
    }

    public static ImmutableSet<String> getCommands() {
        return getCachedConfig().getCommands();
    }

    public static boolean getForceAuth() {
        return getCachedConfig().getForceAuth();
    }

    public static long getMaxAttempts() {
        return getCachedConfig().getMaxAttempts();
    }

    public static FreezeConfigContainer getFreeze() {
        return getCachedConfig().getFreeze();
    }

    public static ImmutableSet<String> getIgnored() {
        return getCachedConfig().getIgnored();
    }

    public static JedisPool getRedisPool() {
        return getCachedConfig().getRedisPool();
    }

    public static ConnectionFactory getRabbitConnectionFactory() {
        return getCachedConfig().getRabbitConnectionFactory();
    }

    public static SQLType getSQLType() {
        return getCachedConfig().getSQLType();
    }

    public static Optional<AuthyApiClient> getAuthy() {
        return getCachedConfig().getAuthy();
    }

    public static ConfigurationNode getStorageConfigNode() {
        return getConfig().getNode("storage");
    }

    public static ConfigurationNode getRedisConfigNode() {
        return getConfig().getNode("redis");
    }
}
