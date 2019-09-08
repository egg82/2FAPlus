package me.egg82.tfaplus.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.AuthenticationData;
import me.egg82.tfaplus.messaging.MessageHandler;
import me.egg82.tfaplus.storage.StorageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAuthenticationHandler<T extends AuthenticationData> implements AuthenticationHandler<T> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Collection<MessageHandler> messageHandlers;
    protected final Collection<StorageHandler> storageHandlers;

    protected final Cache<UUID, T> cache = Caffeine.newBuilder().expireAfterAccess(8L, TimeUnit.HOURS).build();
    private final Object cacheLock = new Object();

    protected AbstractAuthenticationHandler(Collection<MessageHandler> messageHandlers, Collection<StorageHandler> storageHandlers) {
        if (messageHandlers == null) {
            throw new IllegalArgumentException("messageHandlers cannot be null.");
        }
        if (storageHandlers == null) {
            throw new IllegalArgumentException("storageHandlers cannot be null.");
        }

        this.messageHandlers = messageHandlers;
        this.storageHandlers = storageHandlers;
    }

    public boolean isRegistered(UUID uuid) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        return getData(uuid) != null;
    }

    public T getData(UUID uuid) throws APIException {
        T retVal = cache.getIfPresent(uuid);
        if (retVal == null) {
            synchronized (cacheLock) {
                retVal = cache.getIfPresent(uuid);
                if (retVal == null) {
                    retVal = getDataExpensive(uuid);
                    cache.put(uuid, retVal);
                }
            }
        }
        return retVal;
    }

    protected abstract T getDataExpensive(UUID uuid) throws APIException;
}
