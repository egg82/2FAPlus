package me.egg82.tfaplus.services;

import com.authy.AuthyException;
import com.authy.api.Hash;
import com.authy.api.Token;
import com.authy.api.User;
import com.authy.api.Users;
import com.eatthepath.otp.HmacOneTimePasswordGenerator;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.rabbitmq.client.Connection;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.core.*;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.ServiceKeys;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.RabbitMQUtil;
import ninja.egg82.tuples.objects.ObjectObjectPair;
import ninja.leaping.configurate.ConfigurationNode;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InternalAPI {
    private static final Logger logger = LoggerFactory.getLogger(InternalAPI.class);

    private static Cache<ObjectObjectPair<UUID, String>, Boolean> loginCache = Caffeine.newBuilder().expireAfterAccess(1L,TimeUnit.MINUTES).expireAfterWrite(1L,TimeUnit.HOURS).build();
    private static Cache<UUID, Long> authyCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private static Cache<UUID, TOTPCacheData> totpCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private static Cache<UUID, HOTPCacheData> hotpCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private static LoadingCache<UUID, Boolean> verificationCache = Caffeine.newBuilder().expireAfterWrite(3L, TimeUnit.MINUTES).build(k -> Boolean.FALSE);

    private static final Base32 encoder = new Base32();

    public static void changeVerificationTime(long duration, TimeUnit unit) {
        verificationCache = Caffeine.newBuilder().expireAfterWrite(duration, unit).build(k -> Boolean.FALSE);
    }

    public static void add(LoginData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();
        if (storageConfigNode == null) {
            throw new APIException(true, "Could not get config.");
        }

        loginCache.put(new ObjectObjectPair<>(data.getUUID(), data.getIP()), Boolean.TRUE);
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            SQLite.addLogin(data, cachedConfig.get().getSQL(), storageConfigNode);
        }
    }

    public static void add(AuthyData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();
        if (storageConfigNode == null) {
            throw new APIException(true, "Could not get config.");
        }

        authyCache.put(data.getUUID(), data.getID());
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            SQLite.addAuthy(data, cachedConfig.get().getSQL(), storageConfigNode);
        }
    }

    public static void add(TOTPData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();
        if (storageConfigNode == null) {
            throw new APIException(true, "Could not get config.");
        }

        totpCache.put(data.getUUID(), new TOTPCacheData(data.getLength(), data.getKey()));
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            SQLite.addTOTP(data, cachedConfig.get().getSQL(), storageConfigNode);
        }
    }

    public static void add(HOTPData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();
        if (storageConfigNode == null) {
            throw new APIException(true, "Could not get config.");
        }

        hotpCache.put(data.getUUID(), new HOTPCacheData(data.getLength(), data.getCounter(), data.getKey()));
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            SQLite.addHOTP(data, cachedConfig.get().getSQL(), storageConfigNode);
        }
    }

    public String registerHOTP(UUID uuid, long codeLength, long initialCounter) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Registering HOTP " + uuid);
        }

        HmacOneTimePasswordGenerator hotp;
        KeyGenerator keyGen;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) codeLength);
            keyGen = KeyGenerator.getInstance(hotp.getAlgorithm());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        keyGen.init(512);
        SecretKey key = keyGen.generateKey();

        // SQL
        HOTPData result = getHotpData(uuid, codeLength, initialCounter, key);
        if (result == null) {
            throw new APIException(true, "Could not register HOTP data in SQL.");
        }

        // Redis
        Redis.update(result);

        // RabbitMQ
        broadcastResult(result);

        hotpCache.put(uuid, new HOTPCacheData(codeLength, result.getCounter(), key));

        return encoder.encodeToString(key.getEncoded());
    }

    public String registerTOTP(UUID uuid, long codeLength) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Registering TOTP " + uuid);
        }

        TimeBasedOneTimePasswordGenerator totp;
        KeyGenerator keyGen;
        try {
            totp = new TimeBasedOneTimePasswordGenerator(30L, TimeUnit.SECONDS, (int) codeLength, ServiceKeys.TOTP_ALGORITM);
            keyGen = KeyGenerator.getInstance(totp.getAlgorithm());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        keyGen.init(512);
        SecretKey key = keyGen.generateKey();

        // SQL
        TOTPData result = null;
        try {
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.updateTOTP(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, codeLength, key).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.updateTOTP(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, codeLength, key).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result == null) {
            throw new APIException(true, "Could not register TOTP data in SQL.");
        }

        // Redis
        Redis.update(result);

        // RabbitMQ
        try {
            RabbitMQ.broadcast(result, getRabbitConnection());
        } catch (IOException | TimeoutException e) {
            logger.error(e.getMessage(), e);
        }

        totpCache.put(uuid, new TOTPCacheData(codeLength, key));

        return encoder.encodeToString(key.getEncoded());
    }

    private static Connection getRabbitConnection() throws IOException, TimeoutException {
        return RabbitMQUtil.getConnection(ConfigUtil.getCachedConfig().getRabbitConnectionFactory());
    }

    public void registerAuthy(UUID uuid, String email, String phone, String countryCode) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Registering Authy " + uuid + " (" + email + ", +" + countryCode + " " + phone + ")");
        }

        User user;
        try {
           user = getAuthyUsers().createUser(email, phone, countryCode);
        } catch (AuthyException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (!user.isOk()) {
            logger.error(user.getError().getMessage());
            throw new APIException(true, user.getError().getMessage());
        }

        // SQL
        AuthyData result = null;
        try {
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.updateAuthy(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, user.getId()).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.updateAuthy(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, user.getId()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result == null) {
            throw new APIException(true, "Could not register Authy data in SQL.");
        }

        // Redis
        Redis.update(result);

        // RabbitMQ
        try {
            RabbitMQ.broadcast(result, getRabbitConnection());
        } catch (IOException | TimeoutException e) {
            logger.error(e.getMessage(), e);
        }

        authyCache.put(uuid, result.getID());
    }

    private Users getAuthyUsers() {
        return ConfigUtil.getAuthy().get().getUsers();
    }

    public void delete(UUID uuid, Users users) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Removing data for " + uuid);
        }

        for (Map.Entry<ObjectObjectPair<UUID, String>, Boolean> kvp : loginCache.asMap().entrySet()) {
            if (uuid.equals(kvp.getKey().getFirst())) {
                loginCache.invalidate(kvp.getKey());
            }
        }
        hotpCache.invalidate(uuid);
        totpCache.invalidate(uuid);
        authyCache.invalidate(uuid);
        verificationCache.invalidate(uuid);

        AuthyData result = null;
        try {
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.getAuthyData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.getAuthyData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result != null && users != null) {
            Hash response;
            try {
                response = users.deleteUser((int) result.getID());
            } catch (AuthyException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }

            if (!response.isOk()) {
                logger.error(response.getError().getMessage());
                throw new APIException(true, response.getError().getMessage());
            }
        }

        // SQL
        if (ConfigUtil.getSQLType() == SQLType.MySQL) {
            MySQL.delete(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode());
        } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
            SQLite.delete(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode());
        }

        // Redis
        Redis.delete(uuid);

        // RabbitMQ
        rabbitDelete(uuid);
    }

    private void rabbitDelete(UUID uuid) {
        try {
            RabbitMQ.delete(uuid, getRabbitConnection());
        } catch (IOException | TimeoutException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static void delete(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        for (Map.Entry<ObjectObjectPair<UUID, String>, Boolean> kvp : loginCache.asMap().entrySet()) {
            if (uuid.equals(kvp.getKey().getFirst())) {
                loginCache.invalidate(kvp.getKey());
            }
        }
        totpCache.invalidate(uuid);
        authyCache.invalidate(uuid);
        verificationCache.invalidate(uuid);

        if (ConfigUtil.getSQLType() == SQLType.SQLite) {
            SQLite.delete(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode());
        }
    }

    public static void delete(String uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getSQLType() == SQLType.SQLite) {
            SQLite.delete(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode());
        }
    }

    public boolean isVerified(UUID uuid, boolean refresh) {
        boolean retVal = verificationCache.get(uuid);
        if (refresh) {
            verificationCache.put(uuid, retVal);
        }
        return retVal;
    }

    public boolean verifyAuthy(UUID uuid, String token) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Verifying Authy " + uuid + " with " + token);
        }

        long id = authyCache.get(uuid, k -> getAuthyExpensive(uuid));
        if (id < 0) {
            logger.warn(uuid + " has not been registered.");
            throw new APIException(false, "User does not have Authy enabled.");
        }

        Map<String, String> options = new HashMap<>();
        options.put("force", "true");

        Token verification;
        try {
            verification = ConfigUtil.getTokens().verify((int) id, token, options);
        } catch (AuthyException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (!verification.isOk()) {
            logger.error(verification.getError().getMessage());
            return false;
        }

        verificationCache.put(uuid, Boolean.TRUE);
        return true;
    }

    public boolean verifyTOTP(UUID uuid, String token) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Verifying TOTP " + uuid + " with " + token);
        }

        int intToken;
        try {
            intToken = Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(false, "token provided is not an int.");
        }

        TOTPCacheData data = totpCache.get(uuid, k -> getTOTPExpensive(uuid));
        if (data == null) {
            logger.warn(uuid + " has not been registered.");
            throw new APIException(false, "User does not have TOTP enabled.");
        }

        TimeBasedOneTimePasswordGenerator totp;
        try {
            totp = new TimeBasedOneTimePasswordGenerator(30L, TimeUnit.SECONDS, (int) data.getLength(), ServiceKeys.TOTP_ALGORITM);
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        Date now = new Date();
        // Step between 9 codes at different times
        // This allows for a 2-minute drift on either side of the current time
        for (int i = -4; i <= 4; i++) {
            long step = totp.getTimeStep(TimeUnit.MILLISECONDS) * i;
            Date d = new Date(now.getTime() + step);

            try {
                if (totp.generateOneTimePassword(data.getKey(), d) == intToken) {
                    verificationCache.put(uuid, Boolean.TRUE);
                    return true;
                }
            } catch (InvalidKeyException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }
        }

        return false;
    }

    public boolean verifyHOTP(UUID uuid, String token) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Verifying HOTP " + uuid + " with " + token);
        }

        int intToken;
        try {
            intToken = Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(false, "token provided is not an int.");
        }

        HOTPCacheData data = hotpCache.get(uuid, k -> getHOTPExpensive(uuid));
        if (data == null) {
            logger.warn(uuid + " has not been registered.");
            throw new APIException(false, "User does not have TOTP enabled.");
        }

        HmacOneTimePasswordGenerator hotp;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.getLength());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        // Step between 9 codes at different counts
        // This allows for a nice window ahead of the client in case of desync
        for (int i = 0; i <= 9; i++) {
            try {
                if (hotp.generateOneTimePassword(data.getKey(), data.getCounter() + i) == intToken) {
                    setHOTP(uuid, data.getLength(), data.getCounter() + i, data.getKey());
                    verificationCache.put(uuid, Boolean.TRUE);
                    return true;
                }
            } catch (InvalidKeyException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }
        }

        return false;
    }

    private void setHOTP(UUID uuid, long length, long counter, SecretKey key) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Setting new HOTP counter for " + uuid);
        }

        // SQL
        HOTPData result = getHotpData(uuid, length, counter, key);
        if (result == null) {
            return;
        }

        // Redis
        Redis.update(result);

        // Rabbit
        broadcastResult(result);

        // Cache
        hotpCache.put(uuid, new HOTPCacheData(length, counter, key));
    }

    private HOTPData getHotpData(UUID uuid, long length, long counter, SecretKey key) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        HOTPData result = null;
        try {
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.updateHOTP(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, length, counter, key).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.updateHOTP(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, length, counter, key).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }
        return result;
    }

    public boolean seekHOTPCounter(UUID uuid, String[] tokens) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Seeking HOTP counter for " + uuid);
        }

        HOTPCacheData data = hotpCache.get(uuid, k -> getHOTPExpensive(uuid));
        if (data == null) {
            logger.warn(uuid + " has not been registered.");
            return false;
        }

        HmacOneTimePasswordGenerator hotp;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.getLength());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }

        IntList intTokens = new IntArrayList();
        for (String token : tokens) {
            int intToken;
            try {
                intToken = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                logger.error(ex.getMessage(), ex);
                return false;
            }
            intTokens.add(intToken);
        }

        int counter = -1;

        for (int i = 0; i <= 2000; i++) {
            try {
                if (hotp.generateOneTimePassword(data.getKey(), data.getCounter() + i) == intTokens.getInt(0)) {
                    boolean good = true;
                    for (int j = 1; j < intTokens.size(); j++) {
                        if (hotp.generateOneTimePassword(data.getKey(), data.getCounter() + i + j) != intTokens.getInt(j)) {
                            good = false;
                            break;
                        }
                    }

                    if (good) {
                        counter = i;
                        break;
                    }
                }
            } catch (InvalidKeyException ex) {
                logger.error(ex.getMessage(), ex);
                return false;
            }
        }

        if (counter < 0) {
            return false;
        }

        // SQL
        HOTPData result = null;
        result = getHotpData(uuid, data.getLength(), counter, data.getKey(), result);

        if (result == null) {
            return false;
        }

        // Redis
        Redis.update(result);

        // Rabbit
        broadcastResult(result);

        // Cache
        hotpCache.put(uuid, new HOTPCacheData(data.getLength(), counter, data.getKey()));

        return true;
    }

    private void broadcastResult(HOTPData result) {
        try {
            RabbitMQ.broadcast(result, getRabbitConnection());
        } catch (IOException | TimeoutException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public boolean hasAuthy(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting Authy status for " + uuid);
        }

        return authyCache.get(uuid, k -> getAuthyExpensive(uuid)) >= 0L;
    }

    public boolean hasTOTP(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting TOTP status for " + uuid);
        }

        return totpCache.get(uuid, k -> getTOTPExpensive(uuid)) != null;
    }

    public boolean hasHOTP(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting HOTP status for " + uuid);
        }

        return hotpCache.get(uuid, k -> getHOTPExpensive(uuid)) != null;
    }

    public boolean isRegistered(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting registration status for " + uuid);
        }

        return hasAuthy(uuid)
                || hasTOTP(uuid)
                || hasHOTP(uuid);
    }

    private TOTPCacheData getTOTPExpensive(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting TOTP for " + uuid);
        }

        // Redis
        try {
            TOTPCacheData result = Redis.getTOTP(uuid).get();
            if (result != null) {
                if (cachedConfig.get().getDebug()) {
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
            TOTPData result = null;
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.getTOTPData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.getTOTPData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            }

            if (result != null) {
                if (cachedConfig.get().getDebug()) {
                    logger.info(uuid + " found in storage. Value: " + result);
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result).get();
                RabbitMQ.broadcast(result, getRabbitConnection()).get();
                return new TOTPCacheData(result.getLength(), result.getKey());
            }
        } catch (ExecutionException | IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return null;
    }

    private HOTPCacheData getHOTPExpensive(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting HOTP for " + uuid);
        }

        // Redis
        try {
            HOTPCacheData result = Redis.getHOTP(uuid).get();
            if (result != null) {
                if (cachedConfig.get().getDebug()) {
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
            HOTPData result = null;
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.getHOTPData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.getHOTPData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            }

            if (result != null) {
                if (cachedConfig.get().getDebug()) {
                    logger.info(uuid + " found in storage. Value: " + result);
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result).get();
                RabbitMQ.broadcast(result, getRabbitConnection()).get();
                return new HOTPCacheData(result.getLength(), result.getCounter(), result.getKey());
            }
        } catch (ExecutionException | IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return null;
    }

    private long getAuthyExpensive(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting Authy ID for " + uuid);
        }

        // Redis
        try {
            Long result = Redis.getAuthy(uuid).get();
            if (result != null) {
                if (cachedConfig.get().getDebug()) {
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
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.getAuthyData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.getAuthyData(uuid, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            }

            if (result != null) {
                if (cachedConfig.get().getDebug()) {
                    logger.info(uuid + " found in storage. Value: " + result);
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result).get();
                RabbitMQ.broadcast(result, getRabbitConnection()).get();
                return result.getID();
            }
        } catch (ExecutionException | IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return -1L;
    }

    public static void setLogin(UUID uuid, String ip) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Setting login for " + uuid + " (" + ip + ")");
        }

        // SQL
        LoginData result = null;
        try {
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.updateLogin(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, ip).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.updateLogin(ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode(), uuid, ip).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result == null) {
            return;
        }

        // Redis
        Redis.update(result, ConfigUtil.getIPTime());

        // RabbitMQ
        try {
            RabbitMQ.broadcast(result, ConfigUtil.getIPTime(), getRabbitConnection());
        } catch (IOException | TimeoutException e) {
            logger.error(e.getMessage(), e);
        }

        loginCache.put(new ObjectObjectPair<>(uuid, ip), Boolean.TRUE);
    }

    public static boolean getLogin(UUID uuid, String ip, long ipTime) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting login for " + uuid + " (" + ip + ")");
        }

        return loginCache.get(new ObjectObjectPair<>(uuid, ip), k -> getLoginExpensive(uuid, ip, ipTime));
    }

    private static boolean getLoginExpensive(UUID uuid, String ip, long ipTime) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getDebug()) {
            logger.info("Getting expensive login for " + uuid + " (" + ip + ")");
        }

        // Redis
        try {
            Boolean result = Redis.getLogin(uuid, ip).get();
            if (result != null) {
                if (cachedConfig.get().getDebug()) {
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
            if (ConfigUtil.getSQLType() == SQLType.MySQL) {
                result = MySQL.getLoginData(uuid, ip, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            } else if (ConfigUtil.getSQLType() == SQLType.SQLite) {
                result = SQLite.getLoginData(uuid, ip, ConfigUtil.getSQL(), ConfigUtil.getStorageConfigNode()).get();
            }

            if (result != null) {
                if (cachedConfig.get().getDebug()) {
                    logger.info(uuid + " " + ip + " found in storage. Value: " + result);
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result, ipTime).get();
                RabbitMQ.broadcast(result, ipTime, getRabbitConnection()).get();
                return true;
            }
        } catch (ExecutionException | IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return false;
    }
}
