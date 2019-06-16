package me.egg82.tfaplus.services.lookup;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import ninja.egg82.json.JSONUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BukkitPlayerInfo implements PlayerInfo {
    private static final Logger logger = LoggerFactory.getLogger(BukkitPlayerInfo.class);

    private UUID uuid;
    private String name;

    private static LoadingCache<UUID, String> uuidCache = Caffeine.newBuilder().expireAfterWrite(1L, TimeUnit.HOURS).build(k -> getNameExpensive(k));
    private static LoadingCache<String, UUID> nameCache = Caffeine.newBuilder().expireAfterWrite(1L, TimeUnit.HOURS).build(k -> getUUIDExpensive(k));

    public BukkitPlayerInfo(UUID uuid) throws IOException {
        this.uuid = uuid;

        try {
            this.name = uuidCache.get(uuid);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            }
            throw ex;
        }
    }

    public BukkitPlayerInfo(String name) throws IOException {
        this.name = name;

        try {
            this.uuid = nameCache.get(name);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            }
            throw ex;
        }
    }

    public UUID getUUID() { return uuid; }

    public String getName() { return name; }

    private static String getNameExpensive(UUID uuid) throws IOException {
        // Currently-online lookup
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }

        // Network lookup
        HttpURLConnection conn = getConnection("https://api.mojang.com/user/profiles/" + uuid.toString().replace("-", "") + "/names");

        int code = conn.getResponseCode();
        try (InputStream in = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
             InputStreamReader reader = new InputStreamReader(in);
             BufferedReader buffer = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffer.readLine()) != null) {
                builder.append(line);
            }

            if (code == 200) {
                JSONArray json = JSONUtil.parseArray(builder.toString());
                JSONObject last = (JSONObject) json.get(json.size() - 1);
                String name = (String) last.get("name");

                nameCache.put(name, uuid);
            } else if (code == 204) {
                // No data exists
                return null;
            }
        } catch (ParseException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }

    private static UUID getUUIDExpensive(String name) throws IOException {
        // Currently-online lookup
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }

        // Network lookup
        HttpURLConnection conn = getConnection("https://api.mojang.com/users/profiles/minecraft/" + name);

        int code = conn.getResponseCode();
        try (InputStream in = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
             InputStreamReader reader = new InputStreamReader(in);
             BufferedReader buffer = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffer.readLine()) != null) {
                builder.append(line);
            }

            if (code == 200) {
                JSONObject json = JSONUtil.parseObject(builder.toString());
                UUID uuid = UUID.fromString(((String) json.get("id")).replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                name = (String) json.get("name");

                uuidCache.put(uuid, name);
            } else if (code == 204) {
                // No data exists
                return null;
            }
        } catch (ParseException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }

    private static HttpURLConnection getConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        conn.setDoInput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty("User-Agent", "egg82/BukkitPlayerInfo");
        conn.setRequestMethod("GET");

        return conn;
    }
}
