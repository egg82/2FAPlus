package me.egg82.tfaplus.messaging;

import java.util.Set;
import java.util.UUID;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.core.ConnectionData;
import me.egg82.tfaplus.core.LoginData;

public interface MessageHandler {
    void beginHandling();
    void close();

    ConnectionData getConnectionData();
    UUID getHandlerID();

    void broadcast(LoginData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws MessagingException;
    void broadcast(AuthyData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws MessagingException;
    void broadcast(TOTPData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws MessagingException;
    void broadcast(HOTPData data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws MessagingException;

    void delete(UUID uuid, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws MessagingException;
}
