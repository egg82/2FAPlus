package me.egg82.tfaplus;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import me.egg82.tfaplus.utils.ConfigUtil;

public class TFAAPI {
    private static final TFAAPI api = new TFAAPI();
    private final InternalAPI internalApi = new InternalAPI();

    private TFAAPI() {}

    public static TFAAPI getInstance() { return api; }

    public long getCurrentSQLTime() throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        try {
            cachedConfig.get().getDatabase().getCurrentTime();
        } catch (SQLException ex) {
            throw new APIException(true, ex);
        }

        throw new APIException(true, "Could not get time from database.");
    }

    public void registerAuthy(UUID uuid, String email, String phone) throws APIException { registerAuthy(uuid, email, phone, "1"); }

    public void registerAuthy(UUID uuid, String email, String phone, String countryCode) throws APIException {
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

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (!cachedConfig.get().getAuthy().isPresent()) {
            throw new APIException(true, "Authy is not available.");
        }

        internalApi.registerAuthy(uuid, email, phone, countryCode);
    }

    public String registerTOTP(UUID uuid, long codeLength) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength cannot be <= 0.");
        }

        return internalApi.registerTOTP(uuid, codeLength);
    }

    public String registerHOTP(UUID uuid, long codeLength) throws APIException { return registerHOTP(uuid, codeLength, 0L); }

    public String registerHOTP(UUID uuid, long codeLength, long initialCounterValue) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength cannot be <= 0.");
        }
        if (initialCounterValue < 0) {
            throw new IllegalArgumentException("initialCounterValue cannot be < 0.");
        }

        return internalApi.registerHOTP(uuid, codeLength, initialCounterValue);
    }

    public void seekHOTPCounter(UUID uuid, Collection<String> tokens) throws APIException {
        if (tokens == null) {
            throw new IllegalArgumentException("tokens cannot be null.");
        }

        seekHOTPCounter(uuid, tokens.toArray(new String[0]));
    }

    public void seekHOTPCounter(UUID uuid, String[] tokens) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (tokens == null) {
            throw new IllegalArgumentException("tokens cannot be null.");
        }
        if (tokens.length <= 1) {
            throw new IllegalArgumentException("tokens length cannot be <= 1");
        }

        internalApi.seekHOTPCounter(uuid, tokens);
    }

    public boolean isRegistered(UUID uuid) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return internalApi.isRegistered(uuid);
    }

    public void delete(UUID uuid) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        InternalAPI.delete(uuid);
    }

    public boolean isVerified(UUID uuid, boolean refresh) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return internalApi.isVerified(uuid, refresh);
    }

    public boolean verify(UUID uuid, String token) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null.");
        }
        if (token.isEmpty()) {
            throw new IllegalArgumentException("token cannot be empty.");
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getAuthy().isPresent() && internalApi.hasAuthy(uuid)) {
            return internalApi.verifyAuthy(uuid, token);
        } else if (internalApi.hasTOTP(uuid)) {
            return internalApi.verifyTOTP(uuid, token);
        } else if (internalApi.hasHOTP(uuid)) {
            return internalApi.verifyHOTP(uuid, token);
        }

        throw new APIException(false, "User does not have 2FA enabled.");
    }
}
