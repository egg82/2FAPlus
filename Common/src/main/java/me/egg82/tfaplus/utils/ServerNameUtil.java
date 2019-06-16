package me.egg82.tfaplus.utils;

import java.io.*;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerNameUtil {
    private static final Logger logger = LoggerFactory.getLogger(ServerIDUtil.class);

    private ServerNameUtil() {}

    public static String getName(File nameFile) {
        String name;

        try {
            name = readName(nameFile);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            name = null;
        }

        if (name == null || name.isEmpty()) {
            name = "Unnamed";
            try {
                writeName(nameFile, name);
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }

        return name;
    }

    private static String readName(File nameFile) throws IOException {
        if (!nameFile.exists() || (nameFile.exists() && nameFile.isDirectory())) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        try (FileReader reader = new FileReader(nameFile); BufferedReader in = new BufferedReader(reader)) {
            String line;
            while ((line = in.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString().trim();
    }

    private static void writeName(File nameFile, String id) throws IOException {
        if (nameFile.exists() && nameFile.isDirectory()) {
            Files.delete(nameFile.toPath());
        }
        if (!nameFile.exists()) {
            if (!nameFile.createNewFile()) {
                throw new IOException("Name file could not be created.");
            }
        }

        try (FileWriter out = new FileWriter(nameFile)) {
            out.write(id + System.lineSeparator());
        }
    }
}
