package me.egg82.tfaplus.core;

import java.util.UUID;

public class AuthyData extends AuthenticationData {
    private final long id;

    public AuthyData(UUID uuid, long id) {
        super(uuid);
        this.id = id;
    }

    public long getID() { return id; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthyData)) return false;
        AuthyData authyData = (AuthyData) o;
        return uuid.equals(authyData.uuid);
    }
}
