package me.egg82.tfaplus.services;

import com.authy.AuthyException;
import com.authy.api.Hash;
import com.authy.api.Token;
import com.authy.api.User;
import com.eatthepath.otp.HmacOneTimePasswordGenerator;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.core.*;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.ServiceKeys;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import me.egg82.tfaplus.utils.ConfigUtil;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalAPI {
    private static final Logger logger = LoggerFactory.getLogger(InternalAPI.class);

    private static Cache<LoginCacheData, Boolean> loginCache = Caffeine.newBuilder().expireAfterAccess(1L,TimeUnit.MINUTES).expireAfterWrite(1L,TimeUnit.HOURS).build();
    private static Cache<UUID, Long> authyCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private static Cache<UUID, TOTPCacheData> totpCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private static Cache<UUID, HOTPCacheData> hotpCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.HOURS).build();
    private static LoadingCache<UUID, Boolean> verificationCache = Caffeine.newBuilder().expireAfterWrite(3L, TimeUnit.MINUTES).build(k -> Boolean.FALSE);

    private static final Base32 encoder = new Base32();

    private static final Object loginCacheLock = new Object();

    private final Object authyCacheLock = new Object();
    private final Object totpCacheLock = new Object();
    private final Object hotpCacheLock = new Object();

    public static void changeVerificationTime(long duration, TimeUnit unit) {
        verificationCache = Caffeine.newBuilder().expireAfterWrite(duration, unit).build(k -> Boolean.FALSE);
    }

    public static void add(LoginData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        loginCache.put(new LoginCacheData(data.getUUID(), data.getIP()), Boolean.TRUE);
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.addLogin(data);
            } catch (SQLException ex) {
                throw new APIException(true, ex);
            }
        }
    }

    public static void add(AuthyData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        authyCache.put(data.getUUID(), data.getID());
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.addAuthy(data);
            } catch (SQLException ex) {
                throw new APIException(true, ex);
            }
        }
    }

    public static void add(TOTPData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        totpCache.put(data.getUUID(), new TOTPCacheData(data.getLength(), data.getKey()));
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.addTOTP(data);
            } catch (SQLException ex) {
                throw new APIException(true, ex);
            }
        }
    }

    public static void add(HOTPData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        hotpCache.put(data.getUUID(), new HOTPCacheData(data.getLength(), data.getCounter(), data.getKey()));
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.addHOTP(data);
            } catch (SQLException ex) {
                throw new APIException(true, ex);
            }
        }
    }

    public static void setLogin(UUID uuid, String ip) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Setting login for " + uuid + " (" + ip + ")");
        }

        // Update SQL
        LoginData sqlResult = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                sqlResult = MySQL.updateLogin(uuid, ip);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                sqlResult = SQLite.updateLogin(uuid, ip);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (sqlResult == null) {
            throw new APIException(true, "Could not add login in SQL.");
        }

        // Update messaging/Redis
        Redis.update(sqlResult);
        RabbitMQ.broadcast(sqlResult);

        // Update cache
        loginCache.put(new LoginCacheData(uuid, ip), Boolean.TRUE);
    }

    public static boolean getLogin(UUID uuid, String ip) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting login for " + uuid + " (" + ip + ")");
        }

        LoginCacheData key = new LoginCacheData(uuid, ip);

        Optional<Boolean> result = Optional.ofNullable(loginCache.getIfPresent(key));
        if (!result.isPresent()) {
            synchronized (loginCacheLock) {
                result = Optional.ofNullable(loginCache.getIfPresent(key));
                if (!result.isPresent()) {
                    result = Optional.of(loginExpensive(uuid, ip));
                    loginCache.put(key, result.get());
                    return result.get();
                }
            }
        }

        return result.get();
    }

    public String registerHOTP(UUID uuid, long codeLength, long initialCounter) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
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

        keyGen.init(80);
        SecretKey key = keyGen.generateKey();

        // Update SQL
        HOTPData sqlResult = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                sqlResult = MySQL.updateHOTP(uuid, codeLength, initialCounter, key);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                sqlResult = SQLite.updateHOTP(uuid, codeLength, initialCounter, key);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (sqlResult == null) {
            throw new APIException(true, "Could not register HOTP data in SQL.");
        }

        // Update messaging/Redis
        Redis.update(sqlResult);
        RabbitMQ.broadcast(sqlResult);

        // Update cache
        hotpCache.put(uuid, new HOTPCacheData(codeLength, sqlResult.getCounter(), key));

        return encoder.encodeToString(key.getEncoded());
    }

    public String registerTOTP(UUID uuid, long codeLength) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
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

        keyGen.init(80);
        SecretKey key = keyGen.generateKey();

        // Update SQL
        TOTPData sqlResult = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                sqlResult = MySQL.updateTOTP(uuid, codeLength, key);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                sqlResult = SQLite.updateTOTP(uuid, codeLength, key);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (sqlResult == null) {
            throw new APIException(true, "Could not register TOTP data in SQL.");
        }

        // Update messaging/Redis
        Redis.update(sqlResult);
        RabbitMQ.broadcast(sqlResult);

        // Update cache
        totpCache.put(uuid, new TOTPCacheData(codeLength, key));

        return encoder.encodeToString(key.getEncoded());
    }

    public void registerAuthy(UUID uuid, String email, String phone, String countryCode) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent() || !cachedConfig.get().getAuthy().isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Registering Authy " + uuid + " (" + email + ", +" + countryCode + " " + phone + ")");
        }

        User user;
        try {
           user = cachedConfig.get().getAuthy().get().getUsers().createUser(email, phone, countryCode);
        } catch (AuthyException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (!user.isOk()) {
            logger.error(user.getError().getMessage());
            throw new APIException(true, user.getError().getMessage());
        }

        // Update SQL
        AuthyData sqlResult = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                sqlResult = MySQL.updateAuthy(uuid, user.getId());
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                sqlResult = SQLite.updateAuthy(uuid, user.getId());
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (sqlResult == null) {
            throw new APIException(true, "Could not register Authy data in SQL.");
        }

        // Update messaging/Redis
        Redis.update(sqlResult);
        RabbitMQ.broadcast(sqlResult);

        // Update cache
        authyCache.put(uuid, sqlResult.getID());
    }

    public static void delete(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Removing data for " + uuid);
        }

        // Delete authy
        if (cachedConfig.get().getAuthy().isPresent()) {
            Optional<AuthyData> sqlResult = Optional.empty();
            try {
                if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                    sqlResult = MySQL.getAuthyData(uuid);
                } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                    sqlResult = SQLite.getAuthyData(uuid);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }

            if (sqlResult.isPresent()) {
                Hash response;
                try {
                    response = cachedConfig.get().getAuthy().get().getUsers().deleteUser((int) sqlResult.get().getID());
                } catch (AuthyException ex) {
                    logger.error(ex.getMessage(), ex);
                    throw new APIException(true, ex);
                }

                if (!response.isOk()) {
                    logger.error(response.getError().getMessage());
                    throw new APIException(true, response.getError().getMessage());
                }
            }
        }

        // Delete SQL
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                MySQL.delete(uuid);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                SQLite.delete(uuid);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        // Invalidate cache
        for (Map.Entry<LoginCacheData, Boolean> kvp : loginCache.asMap().entrySet()) {
            if (uuid.equals(kvp.getKey().getUUID())) {
                loginCache.invalidate(kvp.getKey());
            }
        }
        hotpCache.invalidate(uuid);
        totpCache.invalidate(uuid);
        authyCache.invalidate(uuid);
        verificationCache.invalidate(uuid);

        // Delete messaging/Redis
        Redis.delete(uuid);
        RabbitMQ.delete(uuid);
    }

    public static void deleteFromMessaging(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        // Delete SQL
        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.delete(uuid);
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }
        }

        // Invalidate cache
        for (Map.Entry<LoginCacheData, Boolean> kvp : loginCache.asMap().entrySet()) {
            if (uuid.equals(kvp.getKey().getUUID())) {
                loginCache.invalidate(kvp.getKey());
            }
        }
        totpCache.invalidate(uuid);
        authyCache.invalidate(uuid);
        verificationCache.invalidate(uuid);
    }

    public boolean isVerified(UUID uuid, boolean refresh) {
        boolean retVal = verificationCache.get(uuid);
        if (refresh) {
            verificationCache.put(uuid, retVal);
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(uuid + " verification token refreshed.");
            }
        }
        return retVal;
    }

    public boolean verifyAuthy(UUID uuid, String token) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent() || !cachedConfig.get().getAuthy().isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Verifying Authy " + uuid + " with " + token);
        }

        Optional<Long> id = Optional.ofNullable(authyCache.getIfPresent(uuid));
        if (!id.isPresent()) {
            synchronized (authyCacheLock) {
                id = Optional.ofNullable(authyCache.getIfPresent(uuid));
                if (!id.isPresent()) {
                    id = authyExpensive(uuid);
                    if (id.isPresent()) {
                        authyCache.put(uuid, id.get());
                    }
                }
            }
        }

        if (!id.isPresent()) {
            logger.warn(uuid + " has not been registered with Authy.");
            throw new APIException(false, "User does not have Authy enabled.");
        }

        Map<String, String> options = new HashMap<>();
        options.put("force", "true");

        Token verification;
        try {
            verification = cachedConfig.get().getAuthy().get().getTokens().verify(id.get().intValue(), token, options);
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

        int intToken;
        try {
            intToken = Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(false, "token provided is not an int.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Verifying TOTP " + uuid + " with " + intToken);
        }

        Optional<TOTPCacheData> data = Optional.ofNullable(totpCache.getIfPresent(uuid));
        if (!data.isPresent()) {
            synchronized (totpCacheLock) {
                data = Optional.ofNullable(totpCache.getIfPresent(uuid));
                if (!data.isPresent()) {
                    data = totpExpensive(uuid);
                    if (data.isPresent()) {
                        totpCache.put(uuid, data.get());
                    }
                }
            }
        }

        if (!data.isPresent()) {
            logger.warn(uuid + " has not been registered with TOTP.");
            throw new APIException(false, "User does not have TOTP enabled.");
        }

        TimeBasedOneTimePasswordGenerator totp;
        try {
            totp = new TimeBasedOneTimePasswordGenerator(30L, TimeUnit.SECONDS, (int) data.get().getLength(), ServiceKeys.TOTP_ALGORITM);
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
                if (totp.generateOneTimePassword(data.get().getKey(), d) == intToken) {
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

        int intToken;
        try {
            intToken = Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(false, "token provided is not an int.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Verifying HOTP " + uuid + " with " + token);
        }

        Optional<HOTPCacheData> data = Optional.ofNullable(hotpCache.getIfPresent(uuid));
        if (!data.isPresent()) {
            synchronized (hotpCacheLock) {
                data = Optional.ofNullable(hotpCache.getIfPresent(uuid));
                if (!data.isPresent()) {
                    data = hotpExpensive(uuid);
                    if (data.isPresent()) {
                        hotpCache.put(uuid, data.get());
                    }
                }
            }
        }

        if (!data.isPresent()) {
            logger.warn(uuid + " has not been registered with HOTP.");
            throw new APIException(false, "User does not have HOTP enabled.");
        }

        HmacOneTimePasswordGenerator hotp;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.get().getLength());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        // Step between 9 codes at different counts
        // This allows for a nice window ahead of the client in case of desync
        for (int i = 0; i <= 9; i++) {
            try {
                if (hotp.generateOneTimePassword(data.get().getKey(), data.get().getCounter() + i) == intToken) {
                    setHOTP(uuid, data.get().getLength(), data.get().getCounter() + i, data.get().getKey());
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

    public void seekHOTPCounter(UUID uuid, String[] tokens) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        IntList intTokens = new IntArrayList();
        for (String token : tokens) {
            int intToken;
            try {
                intToken = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(false, "tokens provided are not ints.");
            }
            intTokens.add(intToken);
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Seeking HOTP counter for " + uuid);
        }

        Optional<HOTPCacheData> data = Optional.ofNullable(hotpCache.getIfPresent(uuid));
        if (!data.isPresent()) {
            synchronized (hotpCacheLock) {
                data = Optional.ofNullable(hotpCache.getIfPresent(uuid));
                if (!data.isPresent()) {
                    data = hotpExpensive(uuid);
                    if (data.isPresent()) {
                        hotpCache.put(uuid, data.get());
                    }
                }
            }
        }

        if (!data.isPresent()) {
            logger.warn(uuid + " has not been registered with HOTP.");
            throw new APIException(false, "User does not have HOTP enabled.");
        }

        HmacOneTimePasswordGenerator hotp;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.get().getLength());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        int counter = -1;

        for (int i = 0; i <= 2000; i++) {
            try {
                if (hotp.generateOneTimePassword(data.get().getKey(), data.get().getCounter() + i) == intTokens.getInt(0)) {
                    boolean good = true;
                    for (int j = 1; j < intTokens.size(); j++) {
                        if (hotp.generateOneTimePassword(data.get().getKey(), data.get().getCounter() + i + j) != intTokens.getInt(j)) {
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
                throw new APIException(true, ex);
            }
        }

        if (counter < 0) {
            throw new APIException(false, "Could not seek HOTP counter form tokens provided.");
        }

        // Update SQL
        HOTPData sqlResult = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                sqlResult = MySQL.updateHOTP(uuid, data.get().getLength(), counter, data.get().getKey());
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                sqlResult = SQLite.updateHOTP(uuid, data.get().getLength(), counter, data.get().getKey());
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (sqlResult == null) {
            throw new APIException(true, "Could not update HOTP data in SQL.");
        }

        // Update messaging/Redis
        Redis.update(sqlResult);
        RabbitMQ.broadcast(sqlResult);

        // Update cache
        hotpCache.put(uuid, new HOTPCacheData(data.get().getLength(), counter, data.get().getKey()));
    }

    private void setHOTP(UUID uuid, long length, long counter, SecretKey key) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Setting new HOTP counter for " + uuid);
        }

        // Update SQL
        HOTPData sqlResult = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                sqlResult = MySQL.updateHOTP(uuid, length, counter, key);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                sqlResult = SQLite.updateHOTP(uuid, length, counter, key);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (sqlResult == null) {
            throw new APIException(true, "Could not update HOTP data in SQL.");
        }

        // Update messaging/Redis
        Redis.update(sqlResult);
        RabbitMQ.broadcast(sqlResult);

        // Update cache
        hotpCache.put(uuid, new HOTPCacheData(length, counter, key));
    }

    public boolean hasAuthy(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting Authy status for " + uuid);
        }

        if (!cachedConfig.get().getAuthy().isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Authy is not enabled. Returning false.");
            }
            return false;
        }

        Optional<Long> id = Optional.ofNullable(authyCache.getIfPresent(uuid));
        if (!id.isPresent()) {
            synchronized (authyCacheLock) {
                id = Optional.ofNullable(authyCache.getIfPresent(uuid));
                if (!id.isPresent()) {
                    id = authyExpensive(uuid);
                    if (id.isPresent()) {
                        authyCache.put(uuid, id.get());
                        if (ConfigUtil.getDebugOrFalse()) {
                            logger.info("Got Authy status for " + uuid + ": " + true);
                        }
                        return true;
                    }
                }
            }
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Got Authy status for " + uuid + ": " + id.isPresent());
        }
        return id.isPresent();
    }

    public boolean hasTOTP(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting TOTP status for " + uuid);
        }

        Optional<TOTPCacheData> data = Optional.ofNullable(totpCache.getIfPresent(uuid));
        if (!data.isPresent()) {
            synchronized (totpCacheLock) {
                data = Optional.ofNullable(totpCache.getIfPresent(uuid));
                if (!data.isPresent()) {
                    data = totpExpensive(uuid);
                    if (data.isPresent()) {
                        totpCache.put(uuid, data.get());
                        if (ConfigUtil.getDebugOrFalse()) {
                            logger.info("Got TOTP status for " + uuid + ": " + true);
                        }
                        return true;
                    }
                }
            }
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Got TOTP status for " + uuid + ": " + data.isPresent());
        }
        return data.isPresent();
    }

    public boolean hasHOTP(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting HOTP status for " + uuid);
        }

        Optional<HOTPCacheData> data = Optional.ofNullable(hotpCache.getIfPresent(uuid));
        if (!data.isPresent()) {
            synchronized (hotpCacheLock) {
                data = Optional.ofNullable(hotpCache.getIfPresent(uuid));
                if (!data.isPresent()) {
                    data = hotpExpensive(uuid);
                    if (data.isPresent()) {
                        hotpCache.put(uuid, data.get());
                        if (ConfigUtil.getDebugOrFalse()) {
                            logger.info("Got HOTP status for " + uuid + ": " + true);
                        }
                        return true;
                    }
                }
            }
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Got HOTP status for " + uuid + ": " + data.isPresent());
        }
        return data.isPresent();
    }

    public boolean isRegistered(UUID uuid) throws APIException {
        return hasAuthy(uuid)
                || hasTOTP(uuid)
                || hasHOTP(uuid);
    }

    private static boolean loginExpensive(UUID uuid, String ip) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting expensive login for " + uuid + " (" + ip + ")");
        }

        // Redis
        Optional<Boolean> redisResult = Redis.getLogin(uuid, ip);
        if (redisResult.isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(uuid + " (" + ip + ") login found in Redis. Value: " + redisResult.get());
            }
            return redisResult.get();
        }

        // SQL
        try {
            Optional<LoginData> result = Optional.empty();
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.getLoginData(uuid, ip);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.getLoginData(uuid, ip);
            }

            if (result.isPresent()) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(uuid + " (" + ip + ") login found in storage. Value: " + result.get());
                }
                // Update messaging/Redis
                Redis.update(result.get());
                RabbitMQ.broadcast(result.get());
                return result.get().getCreated() - System.currentTimeMillis() < cachedConfig.get().getIPCacheTime();
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        return false;
    }

    private Optional<TOTPCacheData> totpExpensive(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting TOTP for " + uuid);
        }

        // Redis
        Optional<TOTPData> redisResult = Redis.getTOTP(uuid);
        if (redisResult.isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(uuid + " TOTP found in Redis. Value: " + redisResult.get());
            }
            return Optional.of(new TOTPCacheData(redisResult.get().getLength(), redisResult.get().getKey()));
        }

        // SQL
        try {
            Optional<TOTPData> result = Optional.empty();
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.getTOTPData(uuid);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.getTOTPData(uuid);
            }

            if (result.isPresent()) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(uuid + " TOTP found in storage. Value: " + result.get());
                }
                // Update messaging/Redis
                Redis.update(result.get());
                RabbitMQ.broadcast(result.get());
                return Optional.of(new TOTPCacheData(result.get().getLength(), result.get().getKey()));
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        return Optional.empty();
    }

    private Optional<HOTPCacheData> hotpExpensive(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting HOTP for " + uuid);
        }

        // Redis
        Optional<HOTPData> redisResult = Redis.getHOTP(uuid);
        if (redisResult.isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(uuid + " HOTP found in Redis. Value: " + redisResult.get());
            }
            return Optional.of(new HOTPCacheData(redisResult.get().getLength(), redisResult.get().getCounter(), redisResult.get().getKey()));
        }

        // SQL
        try {
            Optional<HOTPData> result = Optional.empty();
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.getHOTPData(uuid);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.getHOTPData(uuid);
            }

            if (result.isPresent()) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(uuid + " HOTP found in storage. Value: " + result.get());
                }
                // Update messaging/Redis
                Redis.update(result.get());
                RabbitMQ.broadcast(result.get());
                return Optional.of(new HOTPCacheData(result.get().getLength(), result.get().getCounter(), result.get().getKey()));
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        return Optional.empty();
    }

    private Optional<Long> authyExpensive(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting Authy ID for " + uuid);
        }

        // Redis
        Optional<Long> redisResult = Redis.getAuthy(uuid);
        if (redisResult.isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(uuid + " Authy found in Redis. Value: " + redisResult.get());
            }
            return redisResult;
        }

        // SQL
        try {
            Optional<AuthyData> result = Optional.empty();
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.getAuthyData(uuid);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.getAuthyData(uuid);
            }

            if (result.isPresent()) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(uuid + " Authy found in storage. Value: " + result.get());
                }
                // Update messaging/Redis
                Redis.update(result.get());
                RabbitMQ.broadcast(result.get());
                return Optional.of(result.get().getID());
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        return Optional.empty();
    }
}
