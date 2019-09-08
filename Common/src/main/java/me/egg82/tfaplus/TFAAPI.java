package me.egg82.tfaplus;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.egg82.tfaplus.auth.AuthenticationHandler;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.enums.AuthenticationType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.utils.ConfigUtil;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TFAAPI {
    private static final Logger logger = LoggerFactory.getLogger(TFAAPI.class);

    private static final TFAAPI api = new TFAAPI();

    private TFAAPI() {}

    public static TFAAPI getInstance() { return api; }

    private static LoadingCache<UUID, Boolean> verificationCache = Caffeine.newBuilder().expireAfterWrite(3L, TimeUnit.MINUTES).build(k -> Boolean.FALSE);
    public static void changeVerificationTime(long duration) { verificationCache = Caffeine.newBuilder().expireAfterWrite(duration, TimeUnit.MILLISECONDS).build(k -> Boolean.FALSE); }

    private static final Base32 encoder = new Base32();

    public void registerAuthy(UUID uuid, String email, String phone) throws APIException { registerAuthy(uuid, email, phone, "1"); }

    public void registerAuthy(UUID uuid, String email, String phone, String countryCode) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }
        if (!cachedConfig.get().hasAuthenticationHandler(AuthenticationType.AUTHY)) {
            throw new APIException(true, "Authy is not available.");
        }

        cachedConfig.get().getAuthenticationHandler(AuthenticationType.AUTHY).register(new AuthyData(uuid, email, phone, countryCode), null, null);
    }

    public String registerTOTP(UUID uuid, long codeLength) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }
        if (!cachedConfig.get().hasAuthenticationHandler(AuthenticationType.TOTP)) {
            throw new APIException(true, "TOTP is not available.");
        }

        AuthenticationHandler<TOTPData> handler = cachedConfig.get().getAuthenticationHandler(AuthenticationType.TOTP);
        handler.register(new TOTPData(uuid, codeLength), null, null);
        return encoder.encodeToString(handler.getData(uuid).getKey().getEncoded());
    }

    public String registerHOTP(UUID uuid, long codeLength) throws APIException { return registerHOTP(uuid, codeLength, 0L); }

    public String registerHOTP(UUID uuid, long codeLength, long initialCounterValue) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }
        if (!cachedConfig.get().hasAuthenticationHandler(AuthenticationType.HOTP)) {
            throw new APIException(true, "HOTP is not available.");
        }

        AuthenticationHandler<HOTPData> handler = cachedConfig.get().getAuthenticationHandler(AuthenticationType.HOTP);
        handler.register(new HOTPData(uuid, codeLength, initialCounterValue), null, null);
        return encoder.encodeToString(handler.getData(uuid).getKey().getEncoded());
    }

    public void seekHOTPCounter(UUID uuid, Collection<String> tokens) throws APIException {
        if (tokens == null) {
            throw new IllegalArgumentException("tokens cannot be null.");
        }

        seekHOTPCounter(uuid, tokens.toArray(new String[0]));
    }

    public void seekHOTPCounter(UUID uuid, String[] tokens) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }
        if (!cachedConfig.get().hasAuthenticationHandler(AuthenticationType.HOTP)) {
            throw new APIException(true, "HOTP is not available.");
        }

        AuthenticationHandler<HOTPData> handler = cachedConfig.get().getAuthenticationHandler(AuthenticationType.HOTP);
        handler.seek(uuid, tokens);
    }

    public boolean isRegistered(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        for (AuthenticationHandler<?> handler : cachedConfig.get().getAuthentication().values()) {
            if (handler.isRegistered(uuid)) {
                return true;
            }
        }

        return false;
    }

    public void delete(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        for (AuthenticationHandler<?> handler : cachedConfig.get().getAuthentication().values()) {
            if (handler.isRegistered(uuid)) {
                handler.delete(uuid, true, null, null);
            }
        }
    }

    public boolean isVerified(UUID uuid, boolean refresh) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        boolean retVal = verificationCache.get(uuid);
        if (refresh) {
            verificationCache.put(uuid, retVal);
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Refreshing verification time for " + uuid);
            }
        }
        return retVal;
    }

    public boolean verify(UUID uuid, String token) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        boolean isRegistered = false;
        for (AuthenticationHandler<?> handler : cachedConfig.get().getAuthentication().values()) {
            if (handler.isRegistered(uuid)) {
                isRegistered = true;
                if (handler.verify(uuid, token)) {
                    verificationCache.put(uuid, Boolean.TRUE);
                    return true;
                }
            }
        }

        if (!isRegistered) {
            throw new APIException(false, "User does not have 2FA enabled.");
        }
        return false;
    }
}
