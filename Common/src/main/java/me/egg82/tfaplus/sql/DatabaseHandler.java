package me.egg82.tfaplus.sql;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.core.*;
import me.egg82.tfaplus.enums.SQLType;
import ninja.egg82.sql.SQL;

public interface DatabaseHandler {
    void close();

    boolean isLocal();
    SQL getSQL();
    SQLType getType();
    String getTablePrefix();

    SQLFetchResult loadInfo() throws APIException, SQLException;
    SQLFetchResult fetchQueue() throws APIException, SQLException;

    Optional<LoginData> getLoginData(UUID uuid, String ip) throws APIException, SQLException;
    Optional<AuthyData> getAuthyData(UUID uuid) throws APIException, SQLException;
    Optional<TOTPData> getTOTPData(UUID uuid) throws APIException, SQLException;
    Optional<HOTPData> getHOTPData(UUID uuid) throws APIException, SQLException;

    LoginData updateLogin(LoginData data) throws APIException, SQLException;
    LoginData updateLogin(UUID uuid, String ip) throws APIException, SQLException;
    AuthyData updateAuthy(AuthyData data) throws APIException, SQLException;
    AuthyData updateAuthy(UUID uuid, long id) throws APIException, SQLException;
    TOTPData updateTOTP(TOTPData data) throws APIException, SQLException;
    TOTPData updateTOTP(UUID uuid, long length, SecretKey key) throws APIException, SQLException;
    HOTPData updateHOTP(HOTPData data) throws APIException, SQLException;
    HOTPData updateHOTP(UUID uuid, long length, long counter, SecretKey key) throws APIException, SQLException;

    void delete(UUID uuid) throws SQLException;
    void delete(String uuid) throws SQLException;

    long getCurrentTime() throws APIException, SQLException;
}
