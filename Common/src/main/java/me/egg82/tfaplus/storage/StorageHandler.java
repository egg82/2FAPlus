package me.egg82.tfaplus.storage;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.core.ConnectionData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.storage.data.StorageData;

public interface StorageHandler {
    void close();

    ConnectionData getConnectionData();

    void getAllData(int limitPerChunk, Consumer<StorageData> consumer) throws StorageException;
    void getQueuedData(int limitPerChunk, Consumer<StorageData> consumer) throws StorageException;

    Optional<LoginData> tryGetLoginData(UUID uuid, String ip) throws StorageException;
    Optional<AuthyData> tryGetAuthyData(UUID uuid) throws StorageException;
    Optional<TOTPData> tryGetTOTPData(UUID uuid) throws StorageException;
    Optional<HOTPData> tryGetHOTPData(UUID uuid) throws StorageException;

    void updateLogin(LoginData data) throws StorageException;
    LoginData updateLogin(UUID uuid, String ip) throws StorageException;
    void updateAuthy(AuthyData data) throws StorageException;
    void updateTOTP(TOTPData data) throws StorageException;
    void updateHOTP(HOTPData data) throws StorageException;

    void delete(UUID uuid) throws StorageException;
    void delete(String uuid) throws StorageException;
}
