package me.egg82.tfaplus.core;

import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import me.egg82.tfaplus.extended.ServiceKeys;

public class TOTPData extends AuthenticationData {
    private final long length;
    private final SecretKey key;

    public TOTPData(UUID uuid, long length, byte[] key) {
        super(uuid);

        if (key == null) {
            throw new IllegalArgumentException("key cannot be null.");
        }

        this.length = length;
        this.key = new SecretKeySpec(key, ServiceKeys.TOTP_ALGORITM);
    }

    public TOTPData(UUID uuid, long length, SecretKey key) {
        super(uuid);

        if (key == null) {
            throw new IllegalArgumentException("key cannot be null.");
        }

        this.length = length;
        this.key = key;
    }

    public UUID getUUID() { return uuid; }

    public long getLength() { return length; }

    public SecretKey getKey() { return key; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TOTPData)) return false;
        TOTPData totpData = (TOTPData) o;
        return uuid.equals(totpData.uuid);
    }
}
