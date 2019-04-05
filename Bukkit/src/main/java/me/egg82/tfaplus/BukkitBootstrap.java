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
import me.egg82.tfaplus.utils.LogUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.core.JarDep;
import ninja.egg82.services.ProxiedURLClassLoader;
import ninja.egg82.utils.JarUtil;
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

        JarUtil.loadRawJarFile(getFile(), classLoader);

        JarDep caffeine = JarDep.builder("caffeine", "2.7.0")
                .addURL("https://nexus.egg82.me/repository/maven-central/com/github/ben-manes/caffeine/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/com/github/ben-manes/caffeine/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(caffeine, jarsFolder, classLoader, "Caffeine");

        JarDep rabbitmq = JarDep.builder("amqp-client", "5.6.0")
                .addURL("https://nexus.egg82.me/repository/maven-central/com/rabbitmq/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/com/rabbitmq/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(rabbitmq, jarsFolder, classLoader, "RabbitMQ");

        JarDep hikari = JarDep.builder("HikariCP", "3.2.0")
                .addURL("https://nexus.egg82.me/repository/maven-central/com/zaxxer/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/com/zaxxer/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(hikari, jarsFolder, classLoader, "HikariCP");

        JarDep jedis = JarDep.builder("jedis", "2.9.3")
                .addURL("https://nexus.egg82.me/repository/maven-central/redis/clients/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/redis/clients/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(jedis, jarsFolder, classLoader, "Jedis");

        JarDep javassist = JarDep.builder("javassist", "3.23.1-GA")
                .addURL("https://nexus.egg82.me/repository/maven-central/org/javassist/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/org/javassist/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(javassist, jarsFolder, classLoader, "Javassist");

        JarDep commonsCollections = JarDep.builder("commons-collections", "3.2.2")
                .addURL("https://nexus.egg82.me/repository/maven-central/commons-collections/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/commons-collections/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(commonsCollections, jarsFolder, classLoader, "Apache Commons-Collections");

        JarDep commonsNet = JarDep.builder("commons-net", "3.6")
                .addURL("https://nexus.egg82.me/repository/maven-central/commons-net/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/commons-net/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(commonsNet, jarsFolder, classLoader, "Apache Commons-Net");

        JarDep zxingCore = JarDep.builder("zxing-core", "3.3.3")
                .addURL("https://nexus.egg82.me/repository/maven-central/com/google/zxing/core/{VERSION}/core-{VERSION}.jar") // WARNING: Special case
                .addURL("http://central.maven.org/maven2/com/google/zxing/core/{VERSION}/core-{VERSION}.jar") // WARNING: Special case
                .build();
        loadJar(zxingCore, jarsFolder, classLoader, "ZXing Core");

        JarDep imageioCore = JarDep.builder("jai-imageio-core", "1.4.0")
                .addURL("https://nexus.egg82.me/repository/maven-central/com/github/jai-imageio/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/com/github/jai-imageio/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(imageioCore, jarsFolder, classLoader, "JAI ImageIO Core");

        // 0.9.10 for 1.11 compatibility
        JarDep reflections = JarDep.builder("reflections", "0.9.10")
                .addURL("https://nexus.egg82.me/repository/maven-central/org/reflections/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/org/reflections/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(reflections, jarsFolder, classLoader, "Reflections");

        JarDep sqlite = JarDep.builder("sqlite-jdbc", "3.25.2")
                .addURL("https://nexus.egg82.me/repository/maven-central/org/xerial/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/org/xerial/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(sqlite, jarsFolder, classLoader, "SQLite");

        try {
            DriverManager.registerDriver((Driver) Class.forName("org.sqlite.JDBC", true, classLoader).newInstance());
        } catch (ClassNotFoundException | InstantiationException | SQLException ex) {
            logger.error(ex.getMessage(), ex);
        }

        JarDep mysql = JarDep.builder("mysql-connector-java", "8.0.13")
                .addURL("https://nexus.egg82.me/repository/maven-central/mysql/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/mysql/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(mysql, jarsFolder, classLoader, "MySQL");

        try {
            DriverManager.registerDriver((Driver) Class.forName("com.mysql.jdbc.Driver", true, classLoader).newInstance());
        } catch (ClassNotFoundException | InstantiationException | SQLException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    private void loadJar(JarDep dep, File jarsFolder, URLClassLoader classLoader, String friendlyName) throws IOException, IllegalAccessException, InvocationTargetException {
        if (!JarUtil.hasJar(dep, jarsFolder)) {
            log(Level.INFO, LogUtil.getHeading() + ChatColor.YELLOW + "Downloading " + ChatColor.WHITE + friendlyName);
        }
        JarUtil.loadJar(dep, classLoader, jarsFolder);
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
