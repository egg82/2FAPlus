package me.egg82.tfaplus.auth;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.crypto.KeyGenerator;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.core.ConnectionData;
import me.egg82.tfaplus.enums.AuthenticationType;
import me.egg82.tfaplus.extended.ServiceKeys;
import me.egg82.tfaplus.messaging.MessageHandler;
import me.egg82.tfaplus.messaging.MessagingException;
import me.egg82.tfaplus.storage.StorageException;
import me.egg82.tfaplus.storage.StorageHandler;
import me.egg82.tfaplus.utils.ConfigUtil;

public class TOTP extends AbstractAuthenticationHandler<TOTPData> {
    public TOTP(Collection<MessageHandler> messageHandlers, Collection<StorageHandler> storageHandlers) { super(messageHandlers, storageHandlers); }

    public AuthenticationType getType() { return AuthenticationType.TOTP; }

    public void register(TOTPData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws APIException {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null.");
        }
        if (usedStorage == null) {
            usedStorage = new HashSet<>();
        }
        if (usedMessaging == null) {
            usedMessaging = new HashSet<>();
        }

        if (data.getKey() == null) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Registering TOTP: " + data.getUUID());
            }
            data = register(data);
        } else {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Setting TOTP data: " + data.getUUID());
            }
        }

        boolean isStored = false;
        StorageException lastStorageEx = null;
        for (StorageHandler storageHandler : storageHandlers) {
            if (!usedStorage.add(storageHandler.getConnectionData())) {
                try {
                    storageHandler.updateTOTP(data);
                    isStored = true;
                } catch (StorageException ex) {
                    logger.error(ex.getMessage(), ex);
                    lastStorageEx = ex;
                }
            }
        }

        if (!isStored) {
            if (lastStorageEx != null) {
                throw new APIException(true, "Could not store registration data.", lastStorageEx);
            } else {
                throw new APIException(true, "Could not store registration data. No storage systems available.");
            }
        }

        List<MessageHandler> newHandlers = new ArrayList<>();
        for (MessageHandler messageHandler : messageHandlers) {
            if (!usedMessaging.add(messageHandler.getConnectionData())) {
                newHandlers.add(messageHandler);
            }
        }
        for (MessageHandler messageHandler : newHandlers) {
            try {
                messageHandler.broadcast(data, usedStorage, usedMessaging);
            } catch (MessagingException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }

        cache.put(data.getUUID(), data);
    }

    private TOTPData register(TOTPData data) throws APIException {
        TimeBasedOneTimePasswordGenerator totp;
        KeyGenerator keyGen;
        try {
            totp = new TimeBasedOneTimePasswordGenerator(30L, TimeUnit.SECONDS, (int) data.getLength(), ServiceKeys.TOTP_ALGORITM);
            keyGen = KeyGenerator.getInstance(totp.getAlgorithm());
        } catch (NoSuchAlgorithmException ex) {
            throw new APIException(true, ex);
        }

        keyGen.init(80);
        return new TOTPData(data.getUUID(), data.getLength(), keyGen.generateKey());
    }

    public void delete(UUID uuid, boolean doMainDelete, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (usedStorage == null) {
            usedStorage = new HashSet<>();
        }
        if (usedMessaging == null) {
            usedMessaging = new HashSet<>();
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Deleting TOTP data for " + uuid);
        }

        if (doMainDelete) {
            TOTPData data = getData(uuid);
            if (data == null) {
                throw new APIException(false, "User does not have TOTP enabled.");
            }
        }

        for (StorageHandler storageHandler : storageHandlers) {
            if (!usedStorage.add(storageHandler.getConnectionData())) {
                try {
                    storageHandler.delete(uuid);
                } catch (StorageException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
        }

        List<MessageHandler> newHandlers = new ArrayList<>();
        for (MessageHandler messageHandler : messageHandlers) {
            if (!usedMessaging.add(messageHandler.getConnectionData())) {
                newHandlers.add(messageHandler);
            }
        }
        for (MessageHandler messageHandler : newHandlers) {
            try {
                messageHandler.delete(uuid, usedStorage, usedMessaging);
            } catch (MessagingException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }

        cache.invalidate(uuid);
    }

    public boolean verify(UUID uuid, String code) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (code == null) {
            throw new IllegalArgumentException("code cannot be null.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Verifying TOTP: " + uuid + " with " + code);
        }

        long longCode;
        try {
            longCode = Long.parseLong(code);
        } catch (NumberFormatException ex) {
            throw new APIException(false, "code provided is not a long.");
        }

        TOTPData data = getData(uuid);
        if (data == null) {
            throw new APIException(false, "User does not have TOTP enabled.");
        }

        TimeBasedOneTimePasswordGenerator totp;
        try {
            totp = new TimeBasedOneTimePasswordGenerator(30L, TimeUnit.SECONDS, (int) data.getLength(), ServiceKeys.TOTP_ALGORITM);
        } catch (NoSuchAlgorithmException ex) {
            throw new APIException(true, ex);
        }

        Date now = new Date();
        // Step between 9 codes at different times
        // This allows for a 2-minute drift on either side of the current time
        for (int i = -4; i <= 4; i++) {
            long step = totp.getTimeStep(TimeUnit.MILLISECONDS) * i;
            Date d = new Date(now.getTime() + step);

            try {
                if (totp.generateOneTimePassword(data.getKey(), d) == longCode) {
                    return true;
                }
            } catch (InvalidKeyException ex) {
                throw new APIException(true, ex);
            }
        }

        return false;
    }

    public void seek(UUID uuid, String[] codes) { throw new UnsupportedOperationException("Seek is not supported with TOTP."); }

    protected TOTPData getDataExpensive(UUID uuid) throws APIException {
        StorageException lastStorageEx = null;
        for (StorageHandler storageHandler : storageHandlers) {
            try {
                Optional<TOTPData> data = storageHandler.tryGetTOTPData(uuid);
                if (data.isPresent()) {
                    return data.get();
                }
            } catch (StorageException ex) {
                logger.error(ex.getMessage(), ex);
                lastStorageEx = ex;
            }
        }

        if (lastStorageEx != null) {
            throw new APIException(true, "Could not get stored data.", lastStorageEx);
        }
        return null;
    }
}
