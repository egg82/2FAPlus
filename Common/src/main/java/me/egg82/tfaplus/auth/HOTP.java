package me.egg82.tfaplus.auth;

import com.eatthepath.otp.HmacOneTimePasswordGenerator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.crypto.KeyGenerator;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.core.ConnectionData;
import me.egg82.tfaplus.enums.AuthenticationType;
import me.egg82.tfaplus.messaging.MessageHandler;
import me.egg82.tfaplus.messaging.MessagingException;
import me.egg82.tfaplus.storage.StorageException;
import me.egg82.tfaplus.storage.StorageHandler;
import me.egg82.tfaplus.utils.ConfigUtil;

public class HOTP extends AbstractAuthenticationHandler<HOTPData> {
    public HOTP(Collection<MessageHandler> messageHandlers, Collection<StorageHandler> storageHandlers) { super(messageHandlers, storageHandlers); }

    public AuthenticationType getType() { return AuthenticationType.HOTP; }

    public void register(HOTPData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws APIException {
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
                logger.info("Registering HOTP: " + data.getUUID());
            }
            data = register(data);
        } else {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Setting HOTP data: " + data.getUUID());
            }
        }

        boolean isStored = false;
        StorageException lastStorageEx = null;
        for (StorageHandler storageHandler : storageHandlers) {
            if (!usedStorage.add(storageHandler.getConnectionData())) {
                try {
                    storageHandler.updateHOTP(data);
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

    private HOTPData register(HOTPData data) throws APIException {
        HmacOneTimePasswordGenerator hotp;
        KeyGenerator keyGen;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.getLength());
            keyGen = KeyGenerator.getInstance(hotp.getAlgorithm());
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        keyGen.init(80);
        return new HOTPData(data.getUUID(), data.getLength(), data.getCounter(), keyGen.generateKey());
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
            logger.info("Deleting HOTP data for " + uuid);
        }

        if (doMainDelete) {
            HOTPData data = getData(uuid);
            if (data == null) {
                throw new APIException(false, "User does not have HOTP enabled.");
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
            logger.info("Verifying HOTP: " + uuid + " with " + code);
        }

        long longCode;
        try {
            longCode = Long.parseLong(code);
        } catch (NumberFormatException ex) {
            throw new APIException(false, "code provided is not a long.");
        }

        HOTPData data = getData(uuid);
        if (data == null) {
            throw new APIException(false, "User does not have HOTP enabled.");
        }

        HmacOneTimePasswordGenerator hotp;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.getLength());
        } catch (NoSuchAlgorithmException ex) {
            throw new APIException(true, ex);
        }

        // Step between 9 codes at different counts
        // This allows for a nice window ahead of the client in case of desync
        for (int i = 0; i <= 9; i++) {
            try {
                if (hotp.generateOneTimePassword(data.getKey(), data.getCounter() + i) == longCode) {
                    register(data, null, null);
                    return true;
                }
            } catch (InvalidKeyException ex) {
                throw new APIException(true, ex);
            }
        }

        return false;
    }

    public void seek(UUID uuid, String[] codes) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (codes == null) {
            throw new IllegalArgumentException("codes cannot be null.");
        }
        if (codes.length <= 1) {
            throw new IllegalArgumentException("codes length cannot be <= 1");
        }

        LongList longCodes = new LongArrayList();
        for (String code : codes) {
            try {
                longCodes.add(Long.parseLong(code));
            } catch (NumberFormatException ex) {
                throw new APIException(false, "codes provided are not longs.");
            }
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Seeking HOTP counter for " + uuid);
        }

        HOTPData data = getData(uuid);
        if (data == null) {
            throw new APIException(false, "User does not have HOTP enabled.");
        }

        HmacOneTimePasswordGenerator hotp;
        try {
            hotp = new HmacOneTimePasswordGenerator((int) data.getLength());
        } catch (NoSuchAlgorithmException ex) {
            throw new APIException(true, ex);
        }

        long counter = -1L;
        for (int i = 0; i <= 2000; i++) {
            try {
                if (hotp.generateOneTimePassword(data.getKey(), data.getCounter() + i) == longCodes.getLong(0)) {
                    boolean good = true;
                    for (int j = 1; j < longCodes.size(); j++) {
                        if (hotp.generateOneTimePassword(data.getKey(), data.getCounter() + i + j) != longCodes.getLong(j)) {
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
                throw new APIException(true, ex);
            }
        }

        if (counter < 0L) {
            throw new APIException(false, "Could not seek HOTP counter from codes provided.");
        }

        register(new HOTPData(data.getUUID(), data.getLength(), counter, data.getKey()), null, null);
    }

    protected HOTPData getDataExpensive(UUID uuid) throws APIException {
        StorageException lastStorageEx = null;
        for (StorageHandler storageHandler : storageHandlers) {
            try {
                Optional<HOTPData> data = storageHandler.tryGetHOTPData(uuid);
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
