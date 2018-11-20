package me.egg82.tfaplus.core;

import java.util.UUID;

public class LoginData {
    private final UUID uuid;
    private final String ip;
    private final long created;

    public LoginData(UUID uuid, String ip, long created) {
        this.uuid = uuid;
        this.ip = ip;
        this.created = created;
    }

    public UUID getUUID() { return uuid; }

    public String getIP() { return ip; }

    public long getCreated() { return created; }
}
