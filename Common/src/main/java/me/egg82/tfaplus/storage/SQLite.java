package me.egg82.tfaplus.storage;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;

public class SQLite implements StorageHandler {
    private final SQL sql;
    private final SQLType type;
    private final String tablePrefix;

    public SQLite(SQL sql, SQLType type, String tablePrefix) {
        if (sql == null) {
            throw new IllegalArgumentException("sql cannot be null.");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null.");
        }
        if (tablePrefix == null) {
            tablePrefix = "";
        }

        this.sql = sql;
        this.type = type;
        this.tablePrefix = tablePrefix;
    }

    public void close() { sql.close(); }

    public boolean isLocal() { return true; }

    public SQL getSQL() { return sql; }

    public SQLType getType() { return type; }

    public String getTablePrefix() { return tablePrefix; }

    public SQLFetchResult loadInfo() throws APIException, SQLException {
        List<LoginData> loginData = new ArrayList<>();
        List<AuthyData> authyData = new ArrayList<>();
        List<TOTPData> totpData = new ArrayList<>();
        List<HOTPData> hotpData = new ArrayList<>();
        List<String> removedKeys = new ArrayList<>();

        try {
            SQLQueryResult query = sql.query("SELECT `uuid`, `ip`, `created` FROM `" + tablePrefix + "login`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                    sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", o[0]);
                    continue;
                }
                if (!ValidationUtil.isValidIp((String) o[1])) {
                    removedKeys.add("2faplus:ip:" + o[1]);
                    removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                    sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `ip`=?;", o[1]);
                    continue;
                }

                // Grab all data and convert to more useful object types
                UUID uuid = UUID.fromString((String) o[0]);
                String ip = (String) o[1];
                long created = getTime(o[2]).getTime();

                loginData.add(new LoginData(uuid, ip, created));
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        try {
            SQLQueryResult query = sql.query("SELECT `uuid`, `id` FROM `" + tablePrefix + "authy`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", o[0]);
                    continue;
                }

                // Grab all data and convert to more useful object types
                UUID uuid = UUID.fromString((String) o[0]);
                long id = ((Number) o[1]).longValue();

                authyData.add(new AuthyData(uuid, id));
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        try {
            SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "totp`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    sql.execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", o[0]);
                    continue;
                }

                // Grab all data and convert to more useful object types
                UUID uuid = UUID.fromString((String) o[0]);
                long length = ((Number) o[1]).longValue();
                byte[] key = (byte[]) o[2];

                totpData.add(new TOTPData(uuid, length, key));
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        try {
            SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `counter`, `key` FROM `" + tablePrefix + "hotp`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    sql.execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", o[0]);
                    continue;
                }

                // Grab all data and convert to more useful object types
                UUID uuid = UUID.fromString((String) o[0]);
                long length = ((Number) o[1]).longValue();
                long counter = ((Number) o[2]).longValue();
                byte[] key = (byte[]) o[3];

                hotpData.add(new HOTPData(uuid, length, counter, key));
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return new SQLFetchResult(
                loginData.toArray(new LoginData[0]),
                authyData.toArray(new AuthyData[0]),
                totpData.toArray(new TOTPData[0]),
                hotpData.toArray(new HOTPData[0]),
                removedKeys.toArray(new String[0])
        );
    }

    public SQLFetchResult fetchQueue() { return new SQLFetchResult(new LoginData[0], new AuthyData[0], new TOTPData[0], new HOTPData[0], new String[0]); }

    public Optional<LoginData> getLoginData(UUID uuid, String ip) throws APIException, SQLException {
        LoginData data = null;

        try {
            SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Grab all data and convert to more useful object types
                long created = getTime(o[0]).getTime();
                data = new LoginData(uuid, ip, created);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return Optional.ofNullable(data);
    }

    public Optional<AuthyData> getAuthyData(UUID uuid) throws APIException, SQLException {
        AuthyData data = null;

        try {
            SQLQueryResult query = sql.query("SELECT `id` FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Grab all data and convert to more useful object types
                long id = ((Number) o[0]).longValue();
                data = new AuthyData(uuid, id);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return Optional.ofNullable(data);
    }

    public Optional<TOTPData> getTOTPData(UUID uuid) throws APIException, SQLException {
        TOTPData data = null;

        try {
            SQLQueryResult query = sql.query("SELECT `length`, `key` FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Grab all data and convert to more useful object types
                long length = ((Number) o[0]).longValue();
                byte[] key = (byte[]) o[1];
                data = new TOTPData(uuid, length, key);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return Optional.ofNullable(data);
    }

    public Optional<HOTPData> getHOTPData(UUID uuid) throws APIException, SQLException {
        HOTPData data = null;

        try {
            SQLQueryResult query = sql.query("SELECT `length`, `counter`, `key` FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Grab all data and convert to more useful object types
                long length = ((Number) o[0]).longValue();
                long counter = ((Number) o[1]).longValue();
                byte[] key = (byte[]) o[2];
                data = new HOTPData(uuid, length, counter, key);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return Optional.ofNullable(data);
    }

    public LoginData updateLogin(LoginData data) throws APIException, SQLException { return updateLogin(data.getUUID(), data.getIP()); }

    public LoginData updateLogin(UUID uuid, String ip) throws APIException, SQLException {
        LoginData result = null;

        try {
            sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES (?, ?, (SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?));", uuid.toString(), ip, uuid.toString(), ip);
            SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

            for (Object[] o : query.getData()) {
                long created = getTime(o[0]).getTime();
                result = new LoginData(uuid, ip, created);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return result;
    }

    public AuthyData updateAuthy(AuthyData data) throws SQLException { return updateAuthy(data.getUUID(), data.getID()); }

    public AuthyData updateAuthy(UUID uuid, long id) throws SQLException {
        sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES(?, ?);", uuid.toString(), id);
        return new AuthyData(uuid, id);
    }

    public TOTPData updateTOTP(TOTPData data) throws SQLException { return updateTOTP(data.getUUID(), data.getLength(), data.getKey()); }

    public TOTPData updateTOTP(UUID uuid, long length, SecretKey key) throws SQLException {
        sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES(?, ?, ?);", uuid.toString(), length, key.getEncoded());
        return new TOTPData(uuid, length, key);
    }

    public HOTPData updateHOTP(HOTPData data) throws SQLException { return updateHOTP(data.getUUID(), data.getLength(), data.getCounter(), data.getKey()); }

    public HOTPData updateHOTP(UUID uuid, long length, long counter, SecretKey key) throws SQLException {
        sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?);", uuid.toString(), length, counter, key.getEncoded());
        return new HOTPData(uuid, length, counter, key);
    }

    public void delete(UUID uuid) throws SQLException { delete(uuid.toString()); }

    public void delete(String uuid) throws SQLException {
        sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid);
        sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid);
        sql.execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid);
        sql.execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid);
    }

    public long getCurrentTime() throws APIException, SQLException {
        try {
            long start = System.currentTimeMillis();
            SQLQueryResult query = sql.query("SELECT CURRENT_TIMESTAMP;");

            for (Object[] o : query.getData()) {
                return getTime(o[0]).getTime() + (System.currentTimeMillis() - start);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        throw new APIException(true, "Could not get time from SQL.");
    }

    private static Timestamp getTime(Object o) throws APIException {
        if (o instanceof String) {
            return Timestamp.valueOf((String) o);
        } else if (o instanceof Number) {
            return new Timestamp(((Number) o).longValue());
        }
        throw new APIException(true, "Could not parse time.");
    }
}
