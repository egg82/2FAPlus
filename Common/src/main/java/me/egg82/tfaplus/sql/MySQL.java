package me.egg82.tfaplus.sql;

import me.egg82.tfaplus.core.*;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MySQL {
    private static final Logger logger = LoggerFactory.getLogger(MySQL.class);

    private MySQL() {}

    public static CompletableFuture<Boolean> createTables(SQL sql, ConfigurationNode storageConfigNode) {
        String databaseName = storageConfigNode.getNode("data", "database").getString();
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "login';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "login` ("
                        + "`ip` VARCHAR(45) NOT NULL,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "login` ADD UNIQUE (`ip`, `uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenApplyAsync(v -> {

        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "authy';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "authy` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`id` BIGINT NOT NULL DEFAULT 0"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "authy` ADD UNIQUE (`uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "totp';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "totp` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "totp` ADD UNIQUE (`uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "hotp';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "hotp` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`counter` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "hotp` ADD UNIQUE (`uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "login_queue';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "login_queue` ("
                        + "`ip` VARCHAR(45) NOT NULL,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "login_queue` ADD UNIQUE (`ip`, `uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "authy_queue';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "authy_queue` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`id` BIGINT NOT NULL DEFAULT 0,"
                        + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "authy_queue` ADD UNIQUE (`uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "totp_queue';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "totp_queue` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "totp_queue` ADD UNIQUE (`uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "hotp_queue';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "hotp_queue` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`counter` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "hotp_queue` ADD UNIQUE (`uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<SQLFetchResult> loadInfo(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
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
                    long created = ((Timestamp) o[2]).getTime();

                    loginData.add(new LoginData(uuid, ip, created));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
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
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
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
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "hotp`;");

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
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new SQLFetchResult(loginData.toArray(new LoginData[0]), authyData.toArray(new AuthyData[0]), totpData.toArray(new TOTPData[0]), hotpData.toArray(new HOTPData[0]), removedKeys.toArray(new String[0]));
        });
    }

    public static CompletableFuture<SQLFetchResult> fetchQueue(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            List<LoginData> loginData = new ArrayList<>();
            List<AuthyData> authyData = new ArrayList<>();
            List<TOTPData> totpData = new ArrayList<>();
            List<HOTPData> hotpData = new ArrayList<>();
            List<String> removedKeys = new ArrayList<>();

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `ip`, `created` FROM `" + tablePrefix + "login_queue`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:uuid:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "login_queue` WHERE `uuid`=?;", o[0]);
                        continue;
                    }
                    if (!ValidationUtil.isValidIp((String) o[1])) {
                        removedKeys.add("2faplus:ip:" + o[1]);
                        removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "login_queue` WHERE `ip`=?;", o[1]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    String ip = (String) o[1];
                    long created = ((Timestamp) o[2]).getTime();

                    loginData.add(new LoginData(uuid, ip, created));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `id` FROM `" + tablePrefix + "authy_queue`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:uuid:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        sql.execute("DELETE FROM `" + tablePrefix + "authy_queue` WHERE `uuid`=?;", o[0]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long id = ((Number) o[1]).longValue();

                    authyData.add(new AuthyData(uuid, id));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "totp_queue`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:uuid:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        sql.execute("DELETE FROM `" + tablePrefix + "totp_queue` WHERE `uuid`=?;", o[0]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long length = ((Number) o[1]).longValue();
                    byte[] key = (byte[]) o[2];

                    totpData.add(new TOTPData(uuid, length, key));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `counter`, `key` FROM `" + tablePrefix + "hotp_queue`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:uuid:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        sql.execute("DELETE FROM `" + tablePrefix + "hotp_queue` WHERE `uuid`=?;", o[0]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long length = ((Number) o[1]).longValue();
                    long counter = ((Number) o[2]).longValue();
                    byte[] key = (byte[]) o[3];

                    hotpData.add(new HOTPData(uuid, length, counter, key));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                sql.execute("DELETE FROM `" + tablePrefix + "login_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
                sql.execute("DELETE FROM `" + tablePrefix + "authy_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
                sql.execute("DELETE FROM `" + tablePrefix + "totp_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
                sql.execute("DELETE FROM `" + tablePrefix + "hotp_queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new SQLFetchResult(loginData.toArray(new LoginData[0]), authyData.toArray(new AuthyData[0]), totpData.toArray(new TOTPData[0]), hotpData.toArray(new HOTPData[0]), removedKeys.toArray(new String[0]));
        });
    }

    public static CompletableFuture<LoginData> getLoginData(UUID uuid, String ip, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            LoginData data = null;

            try {
                SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    long created = ((Timestamp) o[0]).getTime();
                    data = new LoginData(uuid, ip, created);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<AuthyData> getAuthyData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            AuthyData data = null;

            try {
                SQLQueryResult query = sql.query("SELECT `id` FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    long id = ((Number) o[0]).longValue();
                    data = new AuthyData(uuid, id);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<TOTPData> getTOTPData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
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
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<HOTPData> getHOTPData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
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
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<LoginData> updateLogin(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, String ip) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            LoginData result = null;

            try {
                sql.execute("INSERT INTO `" + tablePrefix + "login` (`uuid`, `ip`) VALUES(?, ?) ON DUPLICATE KEY UPDATE `created`=CURRENT_TIMESTAMP();", uuid.toString(), ip);
                SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                Timestamp sqlCreated = null;

                for (Object[] o : query.getData()) {
                    sqlCreated = (Timestamp) o[0];
                    result = new LoginData(uuid, ip, sqlCreated.getTime());
                }

                if (sqlCreated != null) {
                    sql.execute("INSERT INTO `" + tablePrefix + "login_queue` (`ip`, `uuid`, `created`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", ip, uuid, sqlCreated);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<AuthyData> updateAuthy(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, long id) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            AuthyData result = null;

            try {
                sql.execute("INSERT INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES(?, ?) ON DUPLICATE KEY UPDATE `id`=?;", uuid.toString(), id, id);
                SQLQueryResult query = sql.query("SELECT `id` FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());

                Long sqlID = null;
                for (Object[] o : query.getData()) {
                    sqlID = ((Number) o[0]).longValue();
                    result = new AuthyData(uuid, id);
                }

                if (sqlID != null) {
                    sql.execute("INSERT INTO `" + tablePrefix + "authy_queue` (`uuid`, `id`) VALUES(?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", uuid, id);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<TOTPData> updateTOTP(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, long length, SecretKey key) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            TOTPData result = null;

            try {
                sql.execute("INSERT INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `length`=?, `key`=?;", uuid.toString(), length, key, length, key);
                SQLQueryResult query = sql.query("SELECT `length`, `key` FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());

                Long sqlLength = null;
                for (Object[] o : query.getData()) {
                    sqlLength = ((Number) o[0]).longValue();
                    result = new TOTPData(uuid, sqlLength, (byte[]) o[1]);
                }

                if (sqlLength != null) {
                    sql.execute("INSERT INTO `" + tablePrefix + "totp_queue` (`uuid`, `length`, `key`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", uuid, length, key);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<HOTPData> updateHOTP(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, long length, long counter, SecretKey key) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            HOTPData result = null;

            try {
                sql.execute("INSERT INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?) ON DUPLICATE KEY UPDATE `length`=?, `counter`=?, `key`=?;", uuid.toString(), length, counter, key, length, counter, key);
                SQLQueryResult query = sql.query("SELECT `length`, `counter`, `key` FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());

                Long sqlLength = null;
                for (Object[] o : query.getData()) {
                    sqlLength = ((Number) o[0]).longValue();
                    result = new HOTPData(uuid, sqlLength, ((Number) o[1]).longValue(), (byte[]) o[2]);
                }

                if (sqlLength != null) {
                    sql.execute("INSERT INTO `" + tablePrefix + "hotp_queue` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?) ON DUPLICATE KEY UPDATE `updated`=CURRENT_TIMESTAMP();", uuid, length, counter, key);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<Void> delete(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Long> getCurrentTime(SQL sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                SQLQueryResult query = sql.query("SELECT CURRENT_TIMESTAMP();");

                for (Object[] o : query.getData()) {
                    return ((Timestamp) o[0]).getTime() + (System.currentTimeMillis() - start);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return -1L;
        });
    }
}
