package me.egg82.tfaplus;

import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import me.egg82.tfaplus.utils.RabbitMQUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TFAAPI {
    private static final Logger logger = LoggerFactory.getLogger(TFAAPI.class);

    private static final TFAAPI api = new TFAAPI();
    private final InternalAPI internalApi = new InternalAPI();

    private TFAAPI() {}

    public static TFAAPI getInstance() { return api; }

    /**
     * Returns the current time in millis according to the SQL database server
     *
     * @return The current time, in millis, from the database server
     */
    public long getCurrentSQLTime() {
        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return -1L;
        }

        try {
            if (cachedConfig.getSQLType() == SQLType.MySQL) {
                return MySQL.getCurrentTime(cachedConfig.getSQL()).get();
            } else if (cachedConfig.getSQLType() == SQLType.SQLite) {
                return SQLite.getCurrentTime(cachedConfig.getSQL()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return -1L;
    }

    /**
     * Register a new Authy user from an existing player
     *
     * @param uuid The player UUID
     * @param email The user's e-mail address
     * @param phone The user's phone number
     * @return Whether or not the registration was successful
     */
    public boolean registerAuthy(UUID uuid, String email, String phone) { return registerAuthy(uuid, email, phone, "1"); }

    /**
     * Register a new Authy user from an existing player
     *
     * @param uuid The player UUID
     * @param email The user's e-mail address
     * @param phone The user's phone number
     * @param countryCode The user's phone numbers' country code
     * @return Whether or not the registration was successful
     */
    public boolean registerAuthy(UUID uuid, String email, String phone, String countryCode) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (email == null) {
            throw new IllegalArgumentException("email cannot be null.");
        }
        if (email.isEmpty()) {
            throw new IllegalArgumentException("email cannot be empty.");
        }
        if (phone == null) {
            throw new IllegalArgumentException("phone cannot be null.");
        }
        if (phone.isEmpty()) {
            throw new IllegalArgumentException("phone cannot be empty.");
        }
        if (countryCode == null) {
            throw new IllegalArgumentException("countryCode cannot be null.");
        }
        if (countryCode.isEmpty()) {
            throw new IllegalArgumentException("countryCode cannot be empty.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }

        if (!cachedConfig.getAuthy().isPresent()) {
            logger.error("Authy is not present (missing API key in config?)");
            return false;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return internalApi.registerAuthy(uuid, email, phone, countryCode, cachedConfig.getAuthy().get().getUsers(), cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return internalApi.registerAuthy(uuid, email, phone, countryCode, cachedConfig.getAuthy().get().getUsers(), cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    /**
     * Register a new TOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the TOTP code (eg. 6 would generate a 6-digit code)
     * @return A base64-encoded private key, or null if error
     */
    public String registerTOTP(UUID uuid, long codeLength) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength cannot be <= 0.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return internalApi.registerTOTP(uuid, codeLength, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return internalApi.registerTOTP(uuid, codeLength, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    /**
     * Returns the current registration status of a player
     *
     * @param uuid The player UUID
     * @return Whether or not the player is currently registered
     */
    public boolean isRegistered(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return internalApi.isRegistered(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return internalApi.isRegistered(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    /**
     * Deletes an existing user from a player
     *
     * @param uuid The player UUID
     * @return Whether or not the deletion was successful
     */
    public boolean delete(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return internalApi.delete(uuid, cachedConfig.getAuthy().isPresent() ? cachedConfig.getAuthy().get().getUsers() : null, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return internalApi.delete(uuid, cachedConfig.getAuthy().isPresent() ? cachedConfig.getAuthy().get().getUsers() : null, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    /**
     * Returns the current verification status of a player
     *
     * @param uuid The player UUID
     * @param refresh Whether or not to reset the verification timer
     * @return Whether or not the player is currently verified through the verification timeout
     */
    public boolean isVerified(UUID uuid, boolean refresh) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return internalApi.isVerified(uuid, refresh);
    }

    /**
     * Forces a verification check for the player
     *
     * @param uuid The player UUID
     * @param token 2FA token to verify against
     * @return An optional boolean value. Empty = error, true = success, false = failure
     */
    public Optional<Boolean> verify(UUID uuid, String token) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null.");
        }
        if (token.isEmpty()) {
            throw new IllegalArgumentException("token cannot be empty.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return Optional.empty();
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            if (cachedConfig.getAuthy().isPresent() && internalApi.hasAuthy(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug())) {
                return internalApi.verifyAuthy(uuid, token, cachedConfig.getAuthy().get().getTokens(), cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
            } else if (internalApi.hasTOTP(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug())) {
                return internalApi.verifyTOTP(uuid, token, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
            } else {
                return Optional.empty();
            }
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        if (cachedConfig.getAuthy().isPresent() && internalApi.hasAuthy(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug())) {
            return internalApi.verifyAuthy(uuid, token, cachedConfig.getAuthy().get().getTokens(), cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } else if (internalApi.hasTOTP(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug())) {
            return internalApi.verifyTOTP(uuid, token, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
        } else {
            return Optional.empty();
        }
    }
}
