package me.egg82.tfaplus.auth;

import java.util.Set;
import java.util.UUID;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.AuthenticationData;
import me.egg82.tfaplus.core.ConnectionData;
import me.egg82.tfaplus.enums.AuthenticationType;

public interface AuthenticationHandler<T extends AuthenticationData> {
    AuthenticationType getType();

    void register(T data, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws APIException;
    void delete(UUID uuid, boolean doMainDelete, Set<ConnectionData> usedStorage, Set<ConnectionData> usedMessaging) throws APIException;

    boolean isRegistered(UUID uuid) throws APIException;
    T getData(UUID uuid) throws APIException;
    boolean verify(UUID uuid, String code) throws APIException;

    void seek(UUID uuid, String[] codes) throws APIException;
}
