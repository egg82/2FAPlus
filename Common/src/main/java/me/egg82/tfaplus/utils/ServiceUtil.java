package me.egg82.tfaplus.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.RabbitMQReceiver;
import me.egg82.tfaplus.extended.RedisSubscriber;
import me.egg82.tfaplus.services.Redis;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServiceUtil.class);

    private static ExecutorService workPool = null;

    private ServiceUtil() {}

    public static void registerRedis() { workPool.submit(RedisSubscriber::new); }

    public static void unregisterRedis() {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        if (cachedConfig.get().getRedisPool() != null) {
            cachedConfig.get().getRedisPool().close();
        }
    }

    public static void registerRabbit() { ServiceLocator.register(new RabbitMQReceiver()); }

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
        } catch (IOException | TimeoutException ignored) { }
    }

    public static void registerSQL() {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try {
            SQLVersionUtil.conformVersion(cachedConfig.get().getDatabase().getType(), cachedConfig.get().getDatabase().getSQL(), cachedConfig.get().getDatabase().getTablePrefix());
            Redis.updateFromQueue(cachedConfig.get().getDatabase().loadInfo());
        } catch (APIException | SQLException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        workPool.submit(ServiceUtil::updateSQL);
    }

    private static void updateSQL() {
        try {
            Thread.sleep(10L * 1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try {
            Redis.updateFromQueue(cachedConfig.get().getDatabase().fetchQueue());
        } catch (APIException | SQLException ex) {
            if (!ex.getMessage().endsWith("has been closed.")) {
                logger.error(ex.getMessage(), ex);
            }
            return;
        }

        workPool.submit(ServiceUtil::updateSQL);
    }

    public static void unregisterSQL() {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        cachedConfig.get().getDatabase().close();
    }

    public static void registerWorkPool() { workPool = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("2FAPlus-Service-%d").build()); }

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
