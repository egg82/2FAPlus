package me.egg82.tfaplus.core;

import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import me.egg82.tfaplus.extended.ServiceKeys;

public class HOTPData {
    private final UUID uuid;
    private final long length;
    private final long counter;
    private final SecretKey key;

    public HOTPData(UUID uuid, long length, long counter, byte[] key) {
        this.uuid = uuid;
        this.length = length;
        this.counter = counter;
        this.key = new SecretKeySpec(key, ServiceKeys.HOTP_ALGORITM);
    }

    public HOTPData(UUID uuid, long length, long counter, SecretKey key) {
        this.uuid = uuid;
        this.length = length;
        this.counter = counter;
        this.key = key;
    }

    public UUID getUUID() { return uuid; }

    public long getLength() { return length; }

    public long getCounter() { return counter; }

    public SecretKey getKey() { return key; }
}
