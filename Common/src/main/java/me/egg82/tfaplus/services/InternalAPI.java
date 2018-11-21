package me.egg82.tfaplus.services;

import com.authy.AuthyException;
import com.authy.api.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.rabbitmq.client.Connection;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import ninja.egg82.sql.SQL;
import ninja.egg82.tuples.objects.ObjectObjectPair;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

public class InternalAPI {
    private static final Logger logger = LoggerFactory.getLogger(InternalAPI.class);

    private static Cache<ObjectObjectPair<UUID, String>, Boolean> loginCache = Caffeine.newBuilder().expireAfterAccess(1L,TimeUnit.MINUTES).expireAfterWrite(1L,TimeUnit.HOURS).build();
    private static Cache<UUID, Long> authyCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.MINUTES).build();
    private static LoadingCache<UUID, Boolean> verificationCache = Caffeine.newBuilder().expireAfterAccess(3L, TimeUnit.MINUTES).build(k -> Boolean.FALSE);

    public static void changeVerificationTime(long duration, TimeUnit unit) {
        verificationCache = Caffeine.newBuilder().expireAfterAccess(duration, unit).build(k -> Boolean.FALSE);
    }

    public static void add(LoginData data, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType) {
        loginCache.put(new ObjectObjectPair<>(data.getUUID(), data.getIP()), Boolean.TRUE);
        if (sqlType == SQLType.SQLite) {
            SQLite.addLogin(data, sql, storageConfigNode);
        }
    }

    public static void add(AuthyData data, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType) {
        authyCache.put(data.getUUID(), data.getID());
        if (sqlType == SQLType.SQLite) {
            SQLite.addAuthy(data, sql, storageConfigNode);
        }
    }

    public boolean register(UUID uuid, String email, String phone, String countryCode, Users users, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Registering " + uuid + " (" + email + ", +" + countryCode + " " + phone + ")");
        }

        User user;
        try {
           user = users.createUser(email, phone, countryCode);
        } catch (AuthyException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }

        if (!user.isOk()) {
            logger.error(user.getError().getMessage());
            return false;
        }

        // SQL
        AuthyData result = null;
        try {
            if (sqlType == SQLType.MySQL) {
                result = MySQL.updateAuthy(sql, storageConfigNode, uuid, user.getId()).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.updateAuthy(sql, storageConfigNode, uuid, user.getId()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result == null) {
            return false;
        }

        // Redis
        Redis.update(result, redisPool, redisConfigNode);

        // RabbitMQ
        RabbitMQ.broadcast(result, rabbitConnection);

        authyCache.put(uuid, result.getID());

        return true;
    }

    public boolean delete(UUID uuid, Users users, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Removing data for " + uuid);
        }

        for (Map.Entry<ObjectObjectPair<UUID, String>, Boolean> kvp : loginCache.asMap().entrySet()) {
            if (uuid.equals(kvp.getKey().getFirst())) {
                loginCache.invalidate(kvp.getKey());
            }
        }
        authyCache.invalidate(uuid);
        verificationCache.invalidate(uuid);

        AuthyData result = null;
        try {
            if (sqlType == SQLType.MySQL) {
                result = MySQL.getAuthyData(uuid, sql, storageConfigNode).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.getAuthyData(uuid, sql, storageConfigNode).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result == null) {
            return false;
        }

        Hash response;
        try {
            response = users.deleteUser((int) result.getID());
        } catch (AuthyException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }

        if (!response.isOk()) {
            logger.error(response.getError().getMessage());
            return false;
        }

        // SQL
        if (sqlType == SQLType.MySQL) {
            MySQL.delete(uuid, sql, storageConfigNode);
        } else if (sqlType == SQLType.SQLite) {
            SQLite.delete(uuid, sql, storageConfigNode);
        }

        // Redis
        Redis.delete(uuid, redisPool, redisConfigNode);

        // RabbitMQ
        RabbitMQ.delete(uuid, rabbitConnection);

        return true;
    }

    public static void delete(UUID uuid, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType) {
        for (Map.Entry<ObjectObjectPair<UUID, String>, Boolean> kvp : loginCache.asMap().entrySet()) {
            if (uuid.equals(kvp.getKey().getFirst())) {
                loginCache.invalidate(kvp.getKey());
            }
        }
        authyCache.invalidate(uuid);
        verificationCache.invalidate(uuid);

        if (sqlType == SQLType.SQLite) {
            SQLite.delete(uuid, sql, storageConfigNode);
        }
    }

    public static void delete(String uuid, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType) {
        if (sqlType == SQLType.SQLite) {
            SQLite.delete(uuid, sql, storageConfigNode);
        }
    }

    public boolean isVerified(UUID uuid) {
        return verificationCache.get(uuid);
    }

    public Optional<Boolean> verify(UUID uuid, String token, Tokens tokens, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Verifying " + uuid + " with " + token);
        }

        long id = authyCache.get(uuid, k -> getAuthyExpensive(uuid, redisPool, redisConfigNode, rabbitConnection, sql, storageConfigNode, sqlType, debug));
        if (id < 0) {
            logger.warn(uuid + " has not been registered.");
            return Optional.empty();
        }

        Token verification;
        try {
            verification = tokens.verify((int) id, token);
        } catch (AuthyException ex) {
            logger.error(ex.getMessage(), ex);
            return Optional.empty();
        }

        if (!verification.isOk()) {
            logger.error(verification.getError().getMessage());
            return Optional.of(Boolean.FALSE);
        }

        verificationCache.put(uuid, Boolean.TRUE);
        return Optional.of(Boolean.TRUE);
    }

    public boolean isRegistered(UUID uuid, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Getting registration status for " + uuid);
        }

        long id = authyCache.get(uuid, k -> getAuthyExpensive(uuid, redisPool, redisConfigNode, rabbitConnection, sql, storageConfigNode, sqlType, debug));
        return id >= 0L;
    }

    private long getAuthyExpensive(UUID uuid, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Getting ID for " + uuid);
        }

        // Redis
        try {
            Long result = Redis.getAuthy(uuid, redisPool, redisConfigNode).get();
            if (result != null) {
                if (debug) {
                    logger.info(uuid + " found in Redis. Value: " + result);
                }
                return result;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        // SQL
        try {
            AuthyData result = null;
            if (sqlType == SQLType.MySQL) {
                result = MySQL.getAuthyData(uuid, sql, storageConfigNode).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.getAuthyData(uuid, sql, storageConfigNode).get();
            }

            if (result != null) {
                if (debug) {
                    logger.info(uuid + " found in storage. Value: " + result);
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result, redisPool, redisConfigNode).get();
                RabbitMQ.broadcast(result, rabbitConnection).get();
                return result.getID();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return -1L;
    }

    public static boolean getLogin(UUID uuid, String ip, long ipTime, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Getting login for " + uuid + " (" + ip + ")");
        }

        return loginCache.get(new ObjectObjectPair<>(uuid, ip), k -> getLoginExpensive(uuid, ip, ipTime, redisPool, redisConfigNode, rabbitConnection, sql, storageConfigNode, sqlType, debug));
    }

    private static boolean getLoginExpensive(UUID uuid, String ip, long ipTime, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Getting expensive login for " + uuid + " (" + ip + ")");
        }

        // Redis
        try {
            Boolean result = Redis.getLogin(uuid, ip, redisPool, redisConfigNode).get();
            if (result != null) {
                if (debug) {
                    logger.info(uuid + " " + ip + " found in Redis. Value: " + result);
                }
                return result;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        // SQL
        try {
            LoginData result = null;
            if (sqlType == SQLType.MySQL) {
                result = MySQL.getLoginData(uuid, ip, sql, storageConfigNode).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.getLoginData(uuid, ip, sql, storageConfigNode).get();
            }

            if (result != null) {
                if (debug) {
                    logger.info(uuid + " " + ip + " found in storage. Value: " + result);
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result, ipTime, redisPool, redisConfigNode).get();
                RabbitMQ.broadcast(result, ipTime, rabbitConnection).get();
                return true;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return false;
    }
}
