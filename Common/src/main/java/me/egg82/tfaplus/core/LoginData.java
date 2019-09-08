package me.egg82.tfaplus.core;

import java.util.Objects;
import java.util.UUID;

public class LoginData {
    private final UUID uuid;
    private final String ip;
    private final long created;

    private final int hashCode;

    public LoginData(UUID uuid, String ip, long created) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }

        this.uuid = uuid;
        this.ip = ip;
        this.created = created;

        hashCode = Objects.hash(uuid, ip);
    }

    public UUID getUUID() { return uuid; }

    public String getIP() { return ip; }

    public long getCreated() { return created; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginData)) return false;
        LoginData loginData = (LoginData) o;
        return uuid.equals(loginData.uuid) &&
                ip.equals(loginData.ip);
    }

    public int hashCode() { return hashCode; }
}
