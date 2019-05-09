package me.egg82.tfaplus.core;

import me.egg82.tfaplus.extended.ServiceKeys;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.UUID;

public class TOTPData {
    private final UUID uuid;
    private final long length;
    private final SecretKey key;

    public TOTPData(UUID uuid, long length, byte[] key) {
        this.uuid = uuid;
        this.length = length;
        this.key = new SecretKeySpec(key, ServiceKeys.TOTP_ALGORITM);
    }

    public TOTPData(UUID uuid, long length, SecretKey key) {
        this.uuid = uuid;
        this.length = length;
        this.key = key;
    }

    public UUID getUUID() { return uuid; }

    public long getLength() { return length; }

    public SecretKey getKey() { return key; }
}
