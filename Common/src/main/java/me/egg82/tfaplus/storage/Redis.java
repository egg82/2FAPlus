package me.egg82.tfaplus.storage;

import me.egg82.tfaplus.services.AbstractRedis;
import redis.clients.jedis.JedisPool;

public class Redis extends AbstractRedis implements StorageHandler {
    public Redis(JedisPool pool) { super(pool); }

    public Redis(JedisPool pool, String password) { super(pool, password); }
}
