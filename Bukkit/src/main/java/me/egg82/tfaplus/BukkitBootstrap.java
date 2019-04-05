package me.egg82.tfaplus;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import me.egg82.tfaplus.utils.LogUtil;
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

        JarDep acfCore = JarDep.builder("acf-core", "0.5.0")
                .addURL("https://nexus.egg82.me/repository/aikar/co/aikar/{NAME}/{VERSION}-SNAPSHOT/{NAME}-{VERSION}-20190401.213847-143-shaded.jar")
                .addURL("https://repo.aikar.co/nexus/content/groups/aikar/co/aikar/{NAME}/{VERSION}-SNAPSHOT/{NAME}-{VERSION}-20190401.213847-143-shaded.jar")
                .build();
        loadJar(acfCore, jarsFolder, classLoader, "ACF Core");

        JarDep acfPaper = JarDep.builder("acf-paper", "0.5.0")
                .addURL("https://nexus.egg82.me/repository/aikar/co/aikar/{NAME}/{VERSION}-SNAPSHOT/{NAME}-{VERSION}-20190401.213856-143-shaded.jar")
                .addURL("https://repo.aikar.co/nexus/content/groups/aikar/co/aikar/{NAME}/{VERSION}-SNAPSHOT/{NAME}-{VERSION}-20190401.213856-143-shaded.jar")
                .build();
        loadJar(acfPaper, jarsFolder, classLoader, "ACF Paper");

        JarDep taskchainCore = JarDep.builder("taskchain-core", "3.7.2")
                .addURL("https://nexus.egg82.me/repository/aikar/co/aikar/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("https://repo.aikar.co/nexus/content/groups/aikar/co/aikar/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(taskchainCore, jarsFolder, classLoader, "Taskchain Core");

        JarDep taskchainBukkit = JarDep.builder("taskchain-bukkit", "3.7.2")
                .addURL("https://nexus.egg82.me/repository/aikar/co/aikar/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("https://repo.aikar.co/nexus/content/groups/aikar/co/aikar/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(taskchainBukkit, jarsFolder, classLoader, "Taskchain Bukkit");

        JarDep eventchainBukkit = JarDep.builder("event-chain-bukkit", "1.0.9")
                .addURL("https://nexus.egg82.me/repository/egg82/ninja.egg82/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("https://www.myget.org/F/egg82-java/maven/ninja.egg82/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(eventchainBukkit, jarsFolder, classLoader, "Event Chain Bukkit");

        JarDep spigotUpdater = JarDep.builder("spigot-updater", "1.0.1")
                .addURL("https://nexus.egg82.me/repository/egg82/ninja.egg82/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("https://www.myget.org/F/egg82-java/maven/ninja.egg82/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(spigotUpdater, jarsFolder, classLoader, "Spigot Updater");

        JarDep reflectionUtils = JarDep.builder("reflection-utils", "1.0.2")
                .addURL("https://nexus.egg82.me/repository/egg82/ninja.egg82/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("https://www.myget.org/F/egg82-java/maven/ninja.egg82/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(reflectionUtils, jarsFolder, classLoader, "Reflection Utils");

        JarDep configurateYAML = JarDep.builder("configurate-yaml", "3.6")
                .addURL("https://nexus.egg82.me/repository/sponge/org/spongepowered/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("https://repo.spongepowered.org/maven/org/spongepowered/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(configurateYAML, jarsFolder, classLoader, "Configurate YAML");

        JarDep beanutils = JarDep.builder("commons-beanutils-core", "1.8.3")
                .addURL("https://nexus.egg82.me/repository/maven-central/commons-beanutils/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/commons-beanutils/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(beanutils, jarsFolder, classLoader, "Apache Commons-Beanutils");

        JarDep validator = JarDep.builder("commons-validator", "1.6")
                .addURL("https://nexus.egg82.me/repository/maven-central/commons-validator/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .addURL("http://central.maven.org/maven2/commons-validator/{NAME}/{VERSION}/{NAME}-{VERSION}.jar")
                .build();
        loadJar(validator, jarsFolder, classLoader, "Apache Commons-Validator");

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

        JarDep zxingJavase = JarDep.builder("zxing-javase", "3.3.3")
                .addURL("https://nexus.egg82.me/repository/maven-central/com/google/zxing/javase/{VERSION}/javase-{VERSION}.jar") // WARNING: Special case
                .addURL("http://central.maven.org/maven2/com/google/zxing/javase/{VERSION}/javase-{VERSION}.jar") // WARNING: Special case
                .build();
        loadJar(zxingJavase, jarsFolder, classLoader, "ZXing JavaSE");

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
}
