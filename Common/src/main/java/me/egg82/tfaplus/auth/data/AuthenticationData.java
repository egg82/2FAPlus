package me.egg82.tfaplus.core;

import java.util.Objects;
import java.util.UUID;

public abstract class AuthenticationData {
    protected final UUID uuid;

    private final int hashCode;

    public AuthenticationData(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        this.uuid = uuid;

        this.hashCode = Objects.hash(uuid);
    }

    public UUID getUUID() { return uuid; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthenticationData)) return false;
        AuthenticationData authenticationData = (AuthenticationData) o;
        return uuid.equals(authenticationData.uuid);
    }

    public int hashCode() { return hashCode; }
}
