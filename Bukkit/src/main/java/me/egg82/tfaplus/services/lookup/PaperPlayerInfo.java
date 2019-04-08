package me.egg82.tfaplus.services.lookup;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PaperPlayerInfo implements PlayerInfo {
    private UUID uuid;
    private String name;

    private static LoadingCache<UUID, String> uuidCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.MINUTES).expireAfterWrite(1L, TimeUnit.HOURS).build(k -> getNameExpensive(k));
    private static LoadingCache<String, UUID> nameCache = Caffeine.newBuilder().expireAfterAccess(1L, TimeUnit.MINUTES).expireAfterWrite(1L, TimeUnit.HOURS).build(k -> getUUIDExpensive(k));

    public PaperPlayerInfo(UUID uuid) throws IOException {
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

    public PaperPlayerInfo(String name) throws IOException {
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

        // Cached profile lookup
        PlayerProfile profile = Bukkit.createProfile(uuid);
        if ((profile.isComplete() || profile.completeFromCache()) && profile.getName() != null && profile.getId() != null) {
            nameCache.put(profile.getName(), profile.getId());
            return profile.getName();
        }

        // Network lookup
        if (profile.complete(false) && profile.getName() != null && profile.getId() != null) {
            nameCache.put(profile.getName(), profile.getId());
            return profile.getName();
        }

        // Sorry, nada
        throw new IOException("Could not load player data from Mojang (rate-limited?)");
    }

    private static UUID getUUIDExpensive(String name) throws IOException {
        // Currently-online lookup
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }

        // Cached profile lookup
        PlayerProfile profile = Bukkit.createProfile(name);
        if ((profile.isComplete() || profile.completeFromCache()) && profile.getName() != null && profile.getId() != null) {
            uuidCache.put(profile.getId(), profile.getName());
            return profile.getId();
        }

        // Network lookup
        if (profile.complete(false) && profile.getName() != null && profile.getId() != null) {
            uuidCache.put(profile.getId(), profile.getName());
            return profile.getId();
        }

        // Sorry, nada
        throw new IOException("Could not load player data from Mojang (rate-limited?)");
    }
}
