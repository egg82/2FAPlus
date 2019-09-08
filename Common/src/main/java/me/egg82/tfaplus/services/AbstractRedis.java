package me.egg82.tfaplus.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class AbstractRedis {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final JedisPool pool;
    private final String password;

    public AbstractRedis(JedisPool pool) { this(pool, null); }

    public AbstractRedis(JedisPool pool, String password) {
        if (pool == null) {
            throw new IllegalArgumentException("pool cannot be null.");
        }

        this.pool = pool;
        this.password = password;
    }

    public void close() {
        try {
            pool.close();
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    protected Jedis getRedis() {
        Jedis retVal = null;
        try {
            retVal = pool.getResource();
            if (password != null && !password.isEmpty()) {
                retVal.auth(password);
            }
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
        return retVal;
    }
}
