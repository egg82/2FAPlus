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
import ninja.leaping.configurate.ConfigurationNode;

public class MySQL {
    private MySQL() {}

    public static void createTables() throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        if (!tableExists(tablePrefix + "login")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "login` ("
                    + "`ip` VARCHAR(45) NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "login` ADD UNIQUE (`ip`, `uuid`);");
        }

        if (!tableExists(tablePrefix + "authy")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "authy` ("
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`id` BIGINT NOT NULL DEFAULT 0"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "authy` ADD UNIQUE (`uuid`);");
        }

        if (!tableExists(tablePrefix + "totp")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "totp` ("
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`length` BIGINT NOT NULL DEFAULT 0,"
                    + "`key` BLOB NOT NULL"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "totp` ADD UNIQUE (`uuid`);");
        }

        if (!tableExists(tablePrefix + "hotp")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "hotp` ("
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`length` BIGINT NOT NULL DEFAULT 0,"
                    + "`counter` BIGINT NOT NULL DEFAULT 0,"
                    + "`key` BLOB NOT NULL"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "hotp` ADD UNIQUE (`uuid`);");
        }

        if (!tableExists(tablePrefix + "login_queue")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "login_queue` ("
                    + "`ip` VARCHAR(45) NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "login_queue` ADD UNIQUE (`ip`, `uuid`);");
        }

        if (!tableExists(tablePrefix + "authy_queue")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "authy_queue` ("
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`id` BIGINT NOT NULL DEFAULT 0,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "authy_queue` ADD UNIQUE (`uuid`);");
        }

        if (!tableExists(tablePrefix + "totp_queue")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "totp_queue` ("
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`length` BIGINT NOT NULL DEFAULT 0,"
                    + "`key` BLOB NOT NULL,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "totp_queue` ADD UNIQUE (`uuid`);");
        }

        if (!tableExists(tablePrefix + "hotp_queue")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "hotp_queue` ("
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`length` BIGINT NOT NULL DEFAULT 0,"
                    + "`counter` BIGINT NOT NULL DEFAULT 0,"
                    + "`key` BLOB NOT NULL,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "hotp_queue` ADD UNIQUE (`uuid`);");
        }
    }

    public static SQLFetchResult loadInfo() throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "hotp`;");

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

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

    private static boolean tableExists(String tableName) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();

        if (!cachedConfig.isPresent() || storageConfigNode == null) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String databaseName = storageConfigNode.getNode("data", "database").getString();

        SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?;", databaseName, tableName);
        return query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0;
    }

    private static String getTablePrefix() throws APIException {
        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();

        if (storageConfigNode == null) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";
        if (tablePrefix.charAt(tablePrefix.length() - 1) != '_') {
            tablePrefix = tablePrefix + "_";
        }
        return tablePrefix;
    }
}
