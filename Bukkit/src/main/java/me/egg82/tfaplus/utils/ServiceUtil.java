package me.egg82.tfaplus.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import me.egg82.tfaplus.core.SQLFetchResult;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.extended.RabbitMQReceiver;
import me.egg82.tfaplus.extended.RedisSubscriber;
import me.egg82.tfaplus.services.Redis;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;

public class ServiceUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServiceUtil.class);

    private static ExecutorService workPool = null;

    private ServiceUtil() {}

    public static void registerRedis() {
        workPool.submit(RedisSubscriber::new);
    }

    public static void unregisterRedis() {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        if (cachedConfig.get().getRedisPool() != null) {
            cachedConfig.get().getRedisPool().close();
        }
    }

    public static void registerRabbit() {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        ServiceLocator.register(new RabbitMQReceiver(cachedConfig.get().getRabbitConnectionFactory()));
    }

    public static void unregisterRabbit() {
        RabbitMQReceiver rabbitReceiver;
        try {
            rabbitReceiver = ServiceLocator.get(RabbitMQReceiver.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        try {
            rabbitReceiver.close();
        } catch (IOException | TimeoutException ignored) {}
    }

    public static void registerSQL() {
        Optional<Configuration> config = ConfigUtil.getConfig();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!config.isPresent() || !cachedConfig.isPresent()) {
            return;
        }

        if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
            MySQL.createTables(cachedConfig.get().getSQL(), config.get().getNode("storage")).thenRun(() ->
                    MySQL.loadInfo(cachedConfig.get().getSQL(), config.get().getNode("storage")).thenAccept(v -> {
                        Redis.updateFromQueue(v, cachedConfig.get().getIPTime());
                        workPool.submit(ServiceUtil::updateSQL);
                    })
            );
        } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            SQLite.createTables(cachedConfig.get().getSQL(), config.get().getNode("storage")).thenRun(() ->
                    SQLite.loadInfo(cachedConfig.get().getSQL(), config.get().getNode("storage")).thenAccept(v -> {
                        Redis.updateFromQueue(v, cachedConfig.get().getIPTime());
                        workPool.submit(ServiceUtil::updateSQL);
                    })
            );
        }
    }

    private static void updateSQL() {
        try {
            Thread.sleep(10L * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        Optional<Configuration> config = ConfigUtil.getConfig();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!config.isPresent() || !cachedConfig.isPresent()) {
            return;
        }

        SQLFetchResult result = null;

        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.fetchQueue(cachedConfig.get().getSQL(), config.get().getNode("storage")).get();
            }

            if (result != null) {
                Redis.updateFromQueue(result, cachedConfig.get().getIPTime()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        workPool.submit(ServiceUtil::updateSQL);
    }

    public static void unregisterSQL() {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        cachedConfig.get().getSQL().close();
    }

    public static void registerWorkPool() {
        workPool = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("2FAPlus-Service-%d").build());
    }

    public static void unregisterWorkPool() {
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
}
