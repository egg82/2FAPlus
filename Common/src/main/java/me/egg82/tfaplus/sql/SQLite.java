package me.egg82.tfaplus.sql;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.core.SQLFetchResult;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLite {
    private static final Logger logger = LoggerFactory.getLogger(SQLite.class);

    private SQLite() {}

    public static CompletableFuture<Void> createTables(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix + "login';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "login` ("
                        + "`ip` TEXT(45) NOT NULL,"
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`ip`, `uuid`)"
                        + ");");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix + "authy';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "authy` ("
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "`id` INTEGER NOT NULL,"
                        + "UNIQUE(`uuid`)"
                        + ");");
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
            List<String> removedKeys = new ArrayList<>();

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `ip`, `created` FROM `" + tablePrefix + "login`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", o[0]);
                        continue;
                    }
                    if (!ValidationUtil.isValidIp((String) o[1])) {
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
                        removedKeys.add("2faplus:authy:" + o[0]);
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

            return new SQLFetchResult(loginData.toArray(new LoginData[0]), authyData.toArray(new AuthyData[0]), removedKeys.toArray(new String[0]));
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
                    long created = getTime(o[0]).getTime();
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

    public static CompletableFuture<LoginData> updateLogin(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, String ip) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            LoginData result = null;

            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES(?, ?, ?) VALUES (?, ?, (SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?));", uuid.toString(), ip, uuid.toString(), ip);
                SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                for (Object[] o : query.getData()) {
                    long created = getTime(o[0]).getTime();
                    result = new LoginData(uuid, ip, created);
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
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES(?, ?);", uuid.toString(), id);
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new AuthyData(uuid, id);
        });
    }

    public static CompletableFuture<Void> addLogin(LoginData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES (?, ?, ?);", data.getUUID().toString(), data.getIP(), data.getCreated());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> addAuthy(AuthyData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES (?, ?);", data.getUUID().toString(), data.getID());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> delete(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> delete(String uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid);
                sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid);
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Long> getCurrentTime(SQL sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                SQLQueryResult query = sql.query("SELECT CURRENT_TIMESTAMP;");

                for (Object[] o : query.getData()) {
                    return getTime(o[0]).getTime() + (System.currentTimeMillis() - start);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return -1L;
        });
    }

    private static Timestamp getTime(Object o) {
        if (o instanceof String) {
            return Timestamp.valueOf((String) o);
        } else if (o instanceof Number) {
            return new Timestamp(((Number) o).longValue());
        }
        return null;
    }
}
