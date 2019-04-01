package me.egg82.tfaplus;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import me.egg82.tfaplus.services.GameAnalyticsErrorHandler;
import me.egg82.tfaplus.services.ProxiedURLClassLoader;
import me.egg82.tfaplus.utils.JarUtil;
import me.egg82.tfaplus.utils.LogUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BukkitBootstrap extends JavaPlugin {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Object concrete;
    private Class<?> concreteClass;

    private final boolean isBukkit;

    private URLClassLoader proxiedClassLoader;

    public BukkitBootstrap() {
        super();
        isBukkit = Bukkit.getName().equals("Bukkit") || Bukkit.getName().equals("CraftBukkit");
    }

    @Override
    public void onLoad() {
        proxiedClassLoader = new ProxiedURLClassLoader(getClass().getClassLoader());

        try {
            loadJars(new File(getDataFolder(), "external"), proxiedClassLoader);
        } catch (ClassCastException | IOException | IllegalAccessException | InvocationTargetException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException("Could not load required deps.");
        }

        try {
            proxiedClassLoader.loadClass("com.zaxxer.hikari.HikariConfig");
        } catch (ClassNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException("Could not load Hikari.");
        }

        try {
            concreteClass = proxiedClassLoader.loadClass("me.egg82.tfaplus.TFAPlus");
            concrete = concreteClass.getDeclaredConstructor(Plugin.class, ClassLoader.class).newInstance(this, proxiedClassLoader);
            concreteClass.getMethod("onLoad").invoke(concrete);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException("Could not create main class.");
        }
    }

    @Override
    public void onEnable() {
        GameAnalyticsErrorHandler.open(getID(), getDescription().getVersion(), Bukkit.getVersion());

        try {
            concreteClass.getMethod("onEnable").invoke(concrete);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException("Could not invoke onEnable.");
        }
    }

    @Override
    public void onDisable() {
        try {
            concreteClass.getMethod("onDisable").invoke(concrete);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException("Could not invoke onDisable.");
        }

        GameAnalyticsErrorHandler.close();
    }

    private void loadJars(File jarsFolder, URLClassLoader classLoader) throws IOException, IllegalAccessException, InvocationTargetException {
        if (jarsFolder.exists() && !jarsFolder.isDirectory()) {
            Files.delete(jarsFolder.toPath());
        }
        if (!jarsFolder.exists()) {
            if (!jarsFolder.mkdirs()) {
                throw new IOException("Could not create parent directory structure.");
            }
        }

        JarUtil.loadJar(getFile(), classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "caffeine-2.6.2.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "Caffeine");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/com/github/ben-manes/caffeine/caffeine/2.6.2/caffeine-2.6.2.jar", "http://central.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/2.6.2/caffeine-2.6.2.jar"),
                new File(jarsFolder, "caffeine-2.6.2.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "amqp-client-5.5.0.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "RabbitMQ");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/com/rabbitmq/amqp-client/5.5.0/amqp-client-5.5.0.jar", "http://central.maven.org/maven2/com/rabbitmq/amqp-client/5.5.0/amqp-client-5.5.0.jar"),
                new File(jarsFolder, "amqp-client-5.5.0.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "HikariCP-3.2.0.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "HikariCP");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/com/zaxxer/HikariCP/3.2.0/HikariCP-3.2.0.jar", "http://central.maven.org/maven2/com/zaxxer/HikariCP/3.2.0/HikariCP-3.2.0.jar"),
                new File(jarsFolder, "HikariCP-3.2.0.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "jedis-2.9.0.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "Redis");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/redis/clients/jedis/2.9.0/jedis-2.9.0.jar", "http://central.maven.org/maven2/redis/clients/jedis/2.9.0/jedis-2.9.0.jar"),
                new File(jarsFolder, "jedis-2.9.0.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "javassist-3.23.1-GA.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "Javassist");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/org/javassist/javassist/3.23.1-GA/javassist-3.23.1-GA.jar", "http://central.maven.org/maven2/org/javassist/javassist/3.23.1-GA/javassist-3.23.1-GA.jar"),
                new File(jarsFolder, "javassist-3.23.1-GA.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "commons-collections-3.2.2.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "Apache Collections");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar", "http://central.maven.org/maven2/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar"),
                new File(jarsFolder, "commons-collections-3.2.2.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "commons-net-3.6.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "Apache Net Utils");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/commons-net/commons-net/3.6/commons-net-3.6.jar", "http://central.maven.org/maven2/commons-net/commons-net/3.6/commons-net-3.6.jar"),
                new File(jarsFolder, "commons-net-3.6.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "zxing-core-3.3.3.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "ZXing Core");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/com/google/zxing/core/3.3.3/core-3.3.3.jar", "http://central.maven.org/maven2/com/google/zxing/core/3.3.3/core-3.3.3.jar"),
                new File(jarsFolder, "zxing-core-3.3.3.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "jai-imageio-core-1.4.0.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "JAI ImageIO Core");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/com/github/jai-imageio/jai-imageio-core/1.4.0/jai-imageio-core-1.4.0.jar", "http://central.maven.org/maven2/com/github/jai-imageio/jai-imageio-core/1.4.0/jai-imageio-core-1.4.0.jar"),
                new File(jarsFolder, "jai-imageio-core-1.4.0.jar"),
                classLoader);

        // 0.9.10 for 1.11 compatibility
        if (!JarUtil.hasJar(new File(jarsFolder, "reflections-0.9.10.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "Reflections");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/org/reflections/reflections/0.9.10/reflections-0.9.10.jar", "http://central.maven.org/maven2/org/reflections/reflections/0.9.10/reflections-0.9.10.jar"),
                new File(jarsFolder, "reflections-0.9.10.jar"),
                classLoader);

        if (!JarUtil.hasJar(new File(jarsFolder, "sqlite-jdbc-3.25.2.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "SQLite");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/org/xerial/sqlite-jdbc/3.25.2/sqlite-jdbc-3.25.2.jar", "http://central.maven.org/maven2/org/xerial/sqlite-jdbc/3.25.2/sqlite-jdbc-3.25.2.jar"),
                new File(jarsFolder, "sqlite-jdbc-3.25.2.jar"),
                classLoader);

        try {
            DriverManager.getDriver("org.sqlite.JDBC");
        } catch (SQLException ignored) {
            try {
                DriverManager.registerDriver((Driver) Class.forName("org.sqlite.JDBC", true, classLoader).newInstance());
            } catch (ClassNotFoundException | InstantiationException | SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }

        if (!JarUtil.hasJar(new File(jarsFolder, "mysql-connector-java-8.0.13.jar"))) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + "MySQL");
        }
        JarUtil.loadJar(Arrays.asList("https://nexus.egg82.me/repository/maven-central/mysql/mysql-connector-java/8.0.13/mysql-connector-java-8.0.13.jar", "http://central.maven.org/maven2/mysql/mysql-connector-java/8.0.13/mysql-connector-java-8.0.13.jar"),
                new File(jarsFolder, "mysql-connector-java-8.0.13.jar"),
                classLoader);

        try {
            DriverManager.getDriver("com.mysql.jdbc.Driver");
        } catch (SQLException ignored) {
            try {
                DriverManager.registerDriver((Driver) Class.forName("com.mysql.jdbc.Driver", true, classLoader).newInstance());
            } catch (ClassNotFoundException | InstantiationException | SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
    }

    private void log(Level level, String message) {
        getServer().getLogger().log(level, (isBukkit) ? ChatColor.stripColor(message) : message);
    }

    private UUID getID() {
        String id = Bukkit.getServerId().trim();
        if (id.isEmpty() || id.equalsIgnoreCase("unnamed") || id.equalsIgnoreCase("unknown") || id.equalsIgnoreCase("default") || !ValidationUtil.isValidUuid(id)) {
            id = UUID.randomUUID().toString();
            try {
                writeID(id);
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }
        return UUID.fromString(id);
    }

    private void writeID(String id) throws IOException {
        File properties = new File(Bukkit.getWorldContainer(), "server.properties");
        if (properties.exists() && properties.isDirectory()) {
            Files.delete(properties.toPath());
        }
        if (!properties.exists()) {
            if (!properties.createNewFile()) {
                throw new IOException("Stats file could not be created.");
            }
        }

        boolean written = false;
        StringBuilder builder = new StringBuilder();
        try (FileReader reader = new FileReader(properties); BufferedReader in = new BufferedReader(reader)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().startsWith("server-id=")) {
                    written = true;
                    builder.append("server-id=" + id).append(System.lineSeparator());
                } else {
                    builder.append(line).append(System.lineSeparator());
                }
            }
        }
        if (!written) {
            builder.append("server-id=" + id).append(System.lineSeparator());
        }

        try (FileWriter out = new FileWriter(properties)) {
            out.write(builder.toString());
        }
    }
}
