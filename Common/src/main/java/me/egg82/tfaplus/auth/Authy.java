package me.egg82.tfaplus.auth;

import com.authy.AuthyApiClient;
import com.authy.AuthyException;
import com.authy.api.Hash;
import com.authy.api.Token;
import com.authy.api.User;
import java.util.*;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.core.ConnectionData;
import me.egg82.tfaplus.enums.AuthenticationType;
import me.egg82.tfaplus.messaging.MessageHandler;
import me.egg82.tfaplus.messaging.MessagingException;
import me.egg82.tfaplus.storage.StorageException;
import me.egg82.tfaplus.storage.StorageHandler;
import me.egg82.tfaplus.utils.ConfigUtil;

public class Authy extends AbstractAuthenticationHandler<AuthyData> {
    private final AuthyApiClient client;

    public Authy(AuthyApiClient client, Collection<MessageHandler> messageHandlers, Collection<StorageHandler> storageHandlers) {
        super(messageHandlers, storageHandlers);

        if (client == null) {
            throw new IllegalArgumentException("client cannot be null.");
        }

        this.client = client;
    }

    public AuthenticationType getType() { return AuthenticationType.AUTHY; }

    public void register(AuthyData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws APIException {
        if (data == null) {
            throw new IllegalArgumentException("data cannot be null.");
        }
        if (usedStorage == null) {
            usedStorage = new HashSet<>();
        }
        if (usedMessaging == null) {
            usedMessaging = new HashSet<>();
        }

        if (data.getID() < 0) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Registering Authy: " + data.getUUID() + " (" + data.getEmail() + ", +" + data.getCountryCode() + " " + data.getPhone() + ")");
            }
            data = register(data);
        } else {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Setting Authy data: " + data.getUUID() + ", " + data.getID());
            }
        }

        boolean isStored = false;
        StorageException lastStorageEx = null;
        for (StorageHandler storageHandler : storageHandlers) {
            if (!usedStorage.add(storageHandler.getConnectionData())) {
                try {
                    storageHandler.updateAuthy(data);
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

    private AuthyData register(AuthyData data) throws APIException {
        User user;
        try {
            user = client.getUsers().createUser(data.getEmail(), data.getPhone(), data.getCountryCode());
        } catch (AuthyException ex) {
            throw new APIException(true, ex);
        }

        if (!user.isOk()) {
            throw new APIException(true, user.getError().getMessage());
        }

        return new AuthyData(data.getUUID(), user.getId());
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
            logger.info("Deleting Authy data for " + uuid);
        }

        if (doMainDelete) {
            AuthyData data = getData(uuid);
            if (data == null) {
                throw new APIException(false, "User does not have Authy enabled.");
            }

            Hash response;
            try {
                response = client.getUsers().deleteUser((int) data.getID());
            } catch (AuthyException ex) {
                throw new APIException(true, ex);
            }

            if (!response.isOk()) {
                throw new APIException(true, response.getError().getMessage());
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
            logger.info("Verifying Authy: " + uuid + " with " + code);
        }

        AuthyData data = getData(uuid);
        if (data == null) {
            throw new APIException(false, "User does not have Authy enabled.");
        }

        Map<String, String> options = new HashMap<>();
        options.put("force", "true");

        Token verification;
        try {
            verification = client.getTokens().verify((int) data.getID(), code, options);
        } catch (AuthyException ex) {
            throw new APIException(true, ex);
        }

        if (!verification.isOk()) {
            logger.warn(verification.getError().getMessage());
            return false;
        }

        return true;
    }

    public void seek(UUID uuid, String[] codes) { throw new UnsupportedOperationException("Seek is not supported with Authy."); }

    protected AuthyData getDataExpensive(UUID uuid) throws APIException {
        StorageException lastStorageEx = null;
        for (StorageHandler storageHandler : storageHandlers) {
            try {
                Optional<AuthyData> data = storageHandler.tryGetAuthyData(uuid);
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
