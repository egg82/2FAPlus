package me.egg82.tfaplus.core;

import javax.crypto.SecretKey;

public class TOTPCacheData {
    private final long length;
    private final SecretKey key;

    public TOTPCacheData(long length, SecretKey key) {
        this.length = length;
        this.key = key;
    }

    public long getLength() { return length; }

    public SecretKey getKey() { return key; }
}
