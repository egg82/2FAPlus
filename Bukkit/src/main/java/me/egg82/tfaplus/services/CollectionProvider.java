package me.egg82.tfaplus.services;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CollectionProvider {
    private CollectionProvider() {}

    private static ConcurrentMap<UUID, Long> frozen = new ConcurrentHashMap<>();
    public static ConcurrentMap<UUID, Long> getFrozen() { return frozen; }
}
