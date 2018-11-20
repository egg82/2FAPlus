package me.egg82.tfaplus.core;

import java.util.UUID;

public class AuthyData {
    private final UUID uuid;
    private final long id;

    public AuthyData(UUID uuid, long id) {
        this.uuid = uuid;
        this.id = id;
    }

    public UUID getUUID() { return uuid; }

    public long getID() { return id; }
}
