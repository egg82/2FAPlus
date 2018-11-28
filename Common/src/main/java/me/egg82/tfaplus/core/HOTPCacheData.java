package me.egg82.tfaplus.core;

import javax.crypto.SecretKey;

public class HOTPCacheData {
    private final long length;
    private final long counter;
    private final SecretKey key;

    public HOTPCacheData(long length, long counter, SecretKey key) {
        this.length = length;
        this.counter = counter;
        this.key = key;
    }

    public long getLength() { return length; }

    public long getCounter() { return counter; }

    public SecretKey getKey() { return key; }
}
