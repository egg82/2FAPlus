package me.egg82.tfaplus.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

public class RedisUtil {
    private RedisUtil() {}

    public static Jedis getRedis() throws JedisException {
        if (ConfigUtil.getRedisConfigNode() == null) {
            throw new IllegalArgumentException("redisConfigNode cannot be null.");
        }

        Jedis redis = null;

        if (ConfigUtil.getRedisPool() != null) {
            redis = ConfigUtil.getRedisPool().getResource();
            String pass = ConfigUtil.getRedisConfigNode().getNode("password").getString();
            if (pass != null && !pass.isEmpty()) {
                redis.auth(pass);
            }
        }

        return redis;
    }
}
