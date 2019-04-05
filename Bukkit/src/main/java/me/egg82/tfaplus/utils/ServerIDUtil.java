package me.egg82.tfaplus.utils;

import org.bukkit.Bukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

public class ServerIDUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServerIDUtil.class);

    public static UUID getID() {
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

    public static void writeID(String id) throws IOException {
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
