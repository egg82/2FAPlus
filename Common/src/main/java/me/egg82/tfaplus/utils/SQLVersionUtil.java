package me.egg82.tfaplus.utils;

import java.sql.SQLException;
import me.egg82.tfaplus.enums.SQLType;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLVersionUtil {
    private static final Logger logger = LoggerFactory.getLogger(SQLVersionUtil.class);

    private SQLVersionUtil() {}

    public static void conformVersion(SQLType type, SQL sql, String tablePrefix) throws SQLException {
        /*double currentVersion = getVersion(type, sql, tablePrefix);

        if (currentVersion == 0.0d) {
            to10(type, sql, tablePrefix);
            currentVersion = 1.0d;
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Updated SQL to version " + currentVersion);
            }
        }
        if (currentVersion == 1.0d) {
            to20(type, sql, tablePrefix);
            currentVersion = 2.0d;
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info("Updated SQL to version " + currentVersion);
            }
        }*/
    }

    private static void to10(SQLType type, SQL sql, String tablePrefix) throws SQLException {
        // Create original tables
        switch (type) {
            case MySQL:
                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "login` ("
                        + "`ip` VARCHAR(45) NOT NULL,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`created` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`ip`, `uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "authy` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`id` BIGINT NOT NULL DEFAULT 0,"
                        + "UNIQUE(`uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "totp` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "UNIQUE(`uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "hotp` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`counter` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "UNIQUE(`uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "login_queue` ("
                        + "`ip` VARCHAR(45) NOT NULL,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`created` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "`updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`ip`, `uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "authy_queue` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`id` BIGINT NOT NULL DEFAULT 0,"
                        + "`updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "totp_queue` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "`updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`uuid`)"
                        + ");");

                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "hotp_queue` ("
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`length` BIGINT NOT NULL DEFAULT 0,"
                        + "`counter` BIGINT NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "`updated` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`uuid`)"
                        + ");");
                break;
            case SQLite:
                if (!sqliteTableExists(sql, tablePrefix + "login")) {
                    sql.execute("CREATE TABLE `" + tablePrefix + "login` ("
                            + "`ip` TEXT(45) NOT NULL,"
                            + "`uuid` TEXT(36) NOT NULL DEFAULT 0,"
                            + "`created` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                            + "UNIQUE(`ip`, `uuid`)"
                            + ");");
                }

                if (!sqliteTableExists(sql, tablePrefix + "authy")) {
                    sql.execute("CREATE TABLE `" + tablePrefix + "authy` ("
                            + "`uuid` TEXT(36) NOT NULL,"
                            + "`id` INTEGER NOT NULL DEFAULT 0,"
                            + "UNIQUE(`uuid`)"
                            + ");");
                }

                if (!sqliteTableExists(sql, tablePrefix + "totp")) {
                    sql.execute("CREATE TABLE `" + tablePrefix + "totp` ("
                            + "`uuid` TEXT(36) NOT NULL,"
                            + "`length` INTEGER NOT NULL DEFAULT 0,"
                            + "`key` BLOB NOT NULL,"
                            + "UNIQUE(`uuid`)"
                            + ");");
                }

                if (!sqliteTableExists(sql, tablePrefix + "hotp")) {
                    sql.execute("CREATE TABLE `" + tablePrefix + "hotp` ("
                            + "`uuid` TEXT(36) NOT NULL,"
                            + "`length` INTEGER NOT NULL DEFAULT 0,"
                            + "`counter` INTEGER NOT NULL DEFAULT 0,"
                            + "`key` BLOB NOT NULL,"
                            + "UNIQUE(`uuid`)"
                            + ");");
                }
                break;
            default:
                throw new SQLException("SQL type not recognized.");
        }

        // Version
        setVersion(type, sql, tablePrefix, 1.0d);
    }

    private static void to20(SQLType type, SQL sql, String tablePrefix) throws SQLException {
        switch (type) {
            case MySQL:
                // Create uuid table
                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "uuid` ("
                        + "`id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "UNIQUE(`uuid`)"
                        + ");");
                break;
            case SQLite:
                // Create uuid table
                sql.execute("CREATE TABLE `" + tablePrefix + "uuid` ("
                        + "`id` INTEGER NOT NULL PRIMARY KEY,"
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "UNIQUE (`uuid`)"
                        + ");");

                // Get existing login data

                // Re-create login table
                sql.execute("DROP TABLE IF EXISTS `" + tablePrefix + "login`;");
                sql.execute("CREATE TABLE `" + tablePrefix + "login` ("
                        + "`ip` TEXT(45) NOT NULL,"
                        + "`id` INTEGER NOT NULL,"
                        + "`created` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`ip`, `id`)"
                        + ");");
                break;
            default:
                throw new SQLException("SQL type not recognized.");
        }

        // Version
        setVersion(type, sql, tablePrefix, 2.0d);
    }

    private static double getVersion(SQLType type, SQL sql, String tablePrefix) throws SQLException {
        switch (type) {
            case MySQL: {
                sql.execute("CREATE TABLE IF NOT EXISTS `" + tablePrefix + "version` ("
                        + "`version` FLOAT NOT NULL DEFAULT 0.0"
                        + ");");
                SQLQueryResult result = sql.query("SELECT `version` FROM `" + tablePrefix + "version` ORDER BY `version` DESC LIMIT 1;");
                if (result.getData().length == 0 || result.getData()[0].length == 0) {
                    return 0.0d;
                }
                return ((Number) result.getData()[0][0]).doubleValue();
            }
            case SQLite: {
                if (!sqliteTableExists(sql, tablePrefix + "version")) {
                    sql.execute("CREATE TABLE `" + tablePrefix + "version` ("
                            + "`version` REAL NOT NULL DEFAULT 0.0"
                            + ");");
                }
                SQLQueryResult result = sql.query("SELECT `version` FROM `" + tablePrefix + "version` ORDER BY `version` DESC LIMIT 1;");
                if (result.getData().length == 0 || result.getData()[0].length == 0) {
                    return 0.0d;
                }
                return ((Number) result.getData()[0][0]).doubleValue();
            }
            default:
                throw new SQLException("SQL type not recognized.");
        }
    }

    private static void setVersion(SQLType type, SQL sql, String tablePrefix, double version) throws SQLException {
        switch (type) {
            case MySQL:
                sql.execute("INSERT INTO `" + tablePrefix + "version` (`version`) VALUES(?);", version);
                break;
            case SQLite:
                sql.execute("INSERT INTO `" + tablePrefix + "version` (`version`) VALUES(?);", version);
                break;
            default:
                throw new SQLException("SQL type not recognized.");
        }
    }

    private static boolean sqliteTableExists(SQL sql, String tableName) throws SQLException {
        SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?;", tableName);
        return query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0;
    }
}
