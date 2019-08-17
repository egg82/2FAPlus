package me.egg82.tfaplus.sql;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.core.*;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;

public class SQLite {
    private SQLite() {}

    public static SQLFetchResult loadInfo() throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        List<LoginData> loginData = new ArrayList<>();
        List<AuthyData> authyData = new ArrayList<>();
        List<TOTPData> totpData = new ArrayList<>();
        List<HOTPData> hotpData = new ArrayList<>();
        List<String> removedKeys = new ArrayList<>();

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `ip`, `created` FROM `" + tablePrefix + "login`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", o[0]);
                    continue;
                }
                if (!ValidationUtil.isValidIp((String) o[1])) {
                    removedKeys.add("2faplus:ip:" + o[1]);
                    removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login` WHERE `ip`=?;", o[1]);
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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `id` FROM `" + tablePrefix + "authy`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", o[0]);
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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "totp`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", o[0]);
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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `length`, `counter`, `key` FROM `" + tablePrefix + "hotp`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", o[0]);
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

    public static Optional<LoginData> getLoginData(UUID uuid, String ip) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        LoginData data = null;

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

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

    public static Optional<AuthyData> getAuthyData(UUID uuid) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        AuthyData data = null;

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `id` FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());

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

    public static Optional<TOTPData> getTOTPData(UUID uuid) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        TOTPData data = null;

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `length`, `key` FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());

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

    public static Optional<HOTPData> getHOTPData(UUID uuid) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        HOTPData data = null;

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `length`, `counter`, `key` FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());

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

    public static LoginData updateLogin(UUID uuid, String ip) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        LoginData result = null;

        try {
            cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES (?, ?, (SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?));", uuid.toString(), ip, uuid.toString(), ip);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

            for (Object[] o : query.getData()) {
                long created = getTime(o[0]).getTime();
                result = new LoginData(uuid, ip, created);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return result;
    }

    public static AuthyData updateAuthy(UUID uuid, long id) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES(?, ?);", uuid.toString(), id);
        return new AuthyData(uuid, id);
    }

    public static TOTPData updateTOTP(UUID uuid, long length, SecretKey key) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES(?, ?, ?);", uuid.toString(), length, key.getEncoded());
        return new TOTPData(uuid, length, key);
    }

    public static HOTPData updateHOTP(UUID uuid, long length, long counter, SecretKey key) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?);", uuid.toString(), length, counter, key.getEncoded());
        return new HOTPData(uuid, length, counter, key);
    }

    public static void addLogin(LoginData data) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES (?, ?, ?);", data.getUUID().toString(), data.getIP(), data.getCreated());
    }

    public static void addAuthy(AuthyData data) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES (?, ?);", data.getUUID().toString(), data.getID());
    }

    public static void addTOTP(TOTPData data) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES (?, ?, ?);", data.getUUID().toString(), data.getLength(), data.getKey().getEncoded());
    }

    public static void addHOTP(HOTPData data) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES (?, ?, ?, ?);", data.getUUID().toString(), data.getLength(), data.getCounter(), data.getKey().getEncoded());
    }

    public static void delete(UUID uuid) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid.toString());
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());
    }

    public static void delete(String uuid) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();

        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid);
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid);
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid);
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid);
    }

    public static Optional<UUID> getUUID(long id) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        UUID uuid = null;

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid` FROM `" + tablePrefix + "uuid` WHERE `id`=?;", id);

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Grab all data and convert to more useful object types
                uuid = UUID.fromString((String) o[0]);
            }
        } catch (ClassCastException | IllegalArgumentException ex) {
            throw new APIException(true, ex);
        }

        return Optional.ofNullable(uuid);
    }

    public static long getCurrentTime() throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        try {
            long start = System.currentTimeMillis();
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT CURRENT_TIMESTAMP;");

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
