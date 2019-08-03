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

public class MySQL {
    private MySQL() {}

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
                long created = ((Timestamp) o[2]).getTime();

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

    public static SQLFetchResult fetchQueue() throws APIException, SQLException {
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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `ip`, `created` FROM `" + tablePrefix + "login_queue`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login_queue` WHERE `uuid`=?;", o[0]);
                    continue;
                }
                if (!ValidationUtil.isValidIp((String) o[1])) {
                    removedKeys.add("2faplus:ip:" + o[1]);
                    removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login_queue` WHERE `ip`=?;", o[1]);
                    continue;
                }

                // Grab all data and convert to more useful object types
                UUID uuid = UUID.fromString((String) o[0]);
                String ip = (String) o[1];
                long created = ((Timestamp) o[2]).getTime();

                loginData.add(new LoginData(uuid, ip, created));
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `id` FROM `" + tablePrefix + "authy_queue`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "authy_queue` WHERE `uuid`=?;", o[0]);
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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "totp_queue`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "totp_queue` WHERE `uuid`=?;", o[0]);
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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `length`, `counter`, `key` FROM `" + tablePrefix + "hotp_queue`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("2faplus:uuid:" + o[0]);
                    removedKeys.add("2faplus:authy:" + o[0]);
                    removedKeys.add("2faplus:totp:" + o[0]);
                    removedKeys.add("2faplus:hotp:" + o[0]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "hotp_queue` WHERE `uuid`=?;", o[0]);
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

        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "login_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "authy_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "totp_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "hotp_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");

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
                long created = ((Timestamp) o[0]).getTime();
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
            cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "login` (`uuid`, `ip`) VALUES(?, ?) ON DUPLICATE KEY UPDATE `created`=CURRENT_TIMESTAMP();", uuid.toString(), ip);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

            Timestamp sqlCreated = null;

            for (Object[] o : query.getData()) {
                sqlCreated = (Timestamp) o[0];
                result = new LoginData(uuid, ip, sqlCreated.getTime());
            }

            if (sqlCreated != null) {
                cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "login_queue` (`ip`, `uuid`, `created`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", ip, uuid, sqlCreated);
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
        AuthyData result = null;

        try {
            cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES(?, ?) ON DUPLICATE KEY UPDATE `id`=?;", uuid.toString(), id, id);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `id` FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());

            Long sqlID = null;
            for (Object[] o : query.getData()) {
                sqlID = ((Number) o[0]).longValue();
                result = new AuthyData(uuid, id);
            }

            if (sqlID != null) {
                cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "authy_queue` (`uuid`, `id`) VALUES(?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", uuid, id);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return result;
    }

    public static TOTPData updateTOTP(UUID uuid, long length, SecretKey key) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        TOTPData result = null;

        try {
            cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `length`=?, `key`=?;", uuid.toString(), length, key, length, key);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `length`, `key` FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());

            Long sqlLength = null;
            for (Object[] o : query.getData()) {
                sqlLength = ((Number) o[0]).longValue();
                result = new TOTPData(uuid, sqlLength, (byte[]) o[1]);
            }

            if (sqlLength != null) {
                cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "totp_queue` (`uuid`, `length`, `key`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", uuid, length, key);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return result;
    }

    public static HOTPData updateHOTP(UUID uuid, long length, long counter, SecretKey key) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = cachedConfig.get().getTablePrefix();
        HOTPData result = null;

        try {
            cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?) ON DUPLICATE KEY UPDATE `length`=?, `counter`=?, `key`=?;", uuid.toString(), length, counter, key, length, counter, key);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `length`, `counter`, `key` FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());

            Long sqlLength = null;
            for (Object[] o : query.getData()) {
                sqlLength = ((Number) o[0]).longValue();
                result = new HOTPData(uuid, sqlLength, ((Number) o[1]).longValue(), (byte[]) o[2]);
            }

            if (sqlLength != null) {
                cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "hotp_queue` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", uuid, length, counter, key);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        return result;
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

    public static long getCurrentTime() throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        try {
            long start = System.currentTimeMillis();
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT CURRENT_TIMESTAMP();");

            for (Object[] o : query.getData()) {
                return ((Timestamp) o[0]).getTime() + (System.currentTimeMillis() - start);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        throw new APIException(true, "Could not get time from SQL.");
    }
}
