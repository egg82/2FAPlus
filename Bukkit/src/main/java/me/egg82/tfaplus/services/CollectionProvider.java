package me.egg82.tfaplus.services;

import ninja.egg82.tuples.longs.LongObjectPair;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CollectionProvider {
    private CollectionProvider() {}

    private static ConcurrentMap<UUID, Long> frozen = new ConcurrentHashMap<>();
    public static ConcurrentMap<UUID, Long> getFrozen() { return frozen; }

    private static ConcurrentMap<UUID, String> commandFrozen = new ConcurrentHashMap<>();
    public static ConcurrentMap<UUID, String> getCommandFrozen() { return commandFrozen; }

    private static ConcurrentMap<UUID, LongObjectPair<List<String>>> hotpFrozen = new ConcurrentHashMap<>();
    public static ConcurrentMap<UUID, LongObjectPair<List<String>>> getHOTPFrozen() { return hotpFrozen; }
}
