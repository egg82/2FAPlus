package me.egg82.tfaplus.utils;

import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

public class ConfigurationVersionUtil {
    private ConfigurationVersionUtil() {}

    public static void conformVersion(ConfigurationLoader<ConfigurationNode> loader, ConfigurationNode config, File fileOnDisk) throws IOException {
        double oldVersion = config.getNode("version").getDouble(1.0d);

        if (config.getNode("version").getDouble(1.0d) == 1.0d) {
            to11(config);
        }
        if (config.getNode("version").getDouble() == 1.1d) {
            to12(config);
        }
        if (config.getNode("version").getDouble() == 1.2d) {
            to13(config);
        }
        if (config.getNode("version").getDouble() == 1.3d) {
            to14(config);
        }
        if (config.getNode("version").getDouble() == 1.4d) {
            to15(config);
        }

        if (config.getNode("version").getDouble() != oldVersion) {
            File backupFile = new File(fileOnDisk.getParent(), fileOnDisk.getName() + ".bak");
            if (backupFile.exists()) {
                java.nio.file.Files.delete(backupFile.toPath());
            }

            Files.copy(fileOnDisk, backupFile);
            loader.save(config);
        }
    }

    private static void to11(ConfigurationNode config) {
        // Add 2FA+ to commands
        List<String> commands;
        try {
            commands = config.getNode("2fa", "command-list").getList(TypeToken.of(String.class));
        } catch (ObjectMappingException ex) {
            commands = new ArrayList<>();
        }
        if (!commands.contains("2faplus")) {
            commands.add("2faplus");
        }
        if (!commands.contains("tfaplus")) {
            commands.add("tfaplus");
        }
        if (!commands.contains("2fa")) {
            commands.add("2fa");
        }
        if (!commands.contains("tfa")) {
            commands.add("tfa");
        }
        config.getNode("2fa", "command-list").setValue(commands);

        // Version
        config.getNode("version").setValue(1.1d);
    }

    private static void to12(ConfigurationNode config) {
        // Add otp
        config.getNode("otp", "digits").setValue(6L);
        config.getNode("otp", "issuer").setValue("2FAPlus");

        // Version
        config.getNode("version").setValue(1.2d);
    }

    private static void to13(ConfigurationNode config) {
        // Add storage->data->SSL
        config.getNode("storage", "data", "ssl").setValue(Boolean.FALSE);

        // Version
        config.getNode("version").setValue(1.3d);
    }

    private static void to14(ConfigurationNode config) {
        // Add 2fa->too-many-attempts-command
        config.getNode("2fa", "too-many-attempts-command").setValue("");

        // Version
        config.getNode("version").setValue(1.4d);
    }

    private static void to15(ConfigurationNode config) {
        // Change name
        if (config.getNode("opt", "issuer").getString("").equalsIgnoreCase("2FAPlus")) {
            config.getNode("otp", "issuer").setValue("2FA+");
        }

        // Version
        config.getNode("version").setValue(1.5d);
    }
}
