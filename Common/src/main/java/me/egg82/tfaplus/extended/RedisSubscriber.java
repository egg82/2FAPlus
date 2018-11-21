package me.egg82.tfaplus.extended;

import java.util.UUID;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.services.InternalAPI;
import me.egg82.altfinder.services.Redis;
import me.egg82.altfinder.utils.RedisUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.json.JSONUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import ninja.leaping.configurate.ConfigurationNode;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisException;

public class RedisSubscriber {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public RedisSubscriber(JedisPool pool, ConfigurationNode redisConfigNode) {
        try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
            if (redis == null) {
                return;
            }

            redis.subscribe(new Subscriber(), "altfndr-info", "altfndr-delete");
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    class Subscriber extends JedisPubSub {
        private Subscriber() { super(); }

        public void onMessage(String channel, String message) {
            if (channel.equals("altfndr-info")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    String ip = (String) obj.get("ip");
                    long count = ((Number) obj.get("count")).longValue();
                    String server = (String) obj.get("server");
                    long created = ((Number) obj.get("created")).longValue();
                    long updated = ((Number) obj.get("updated")).longValue();
                    UUID id = UUID.fromString((String) obj.get("id"));

                    if (!ValidationUtil.isValidIp(ip)) {
                        logger.warn("non-valid IP sent through Redis pub/sub");
                        return;
                    }

                    if (id.equals(Redis.getServerID())) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                    Configuration config = ServiceLocator.get(Configuration.class);

                    InternalAPI.add(new PlayerData(uuid, ip, count, server, created, updated), cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("altfndr-delete")) {
                CachedConfigValues cachedConfig;
                Configuration config;

                try {
                    cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                    config = ServiceLocator.get(Configuration.class);
                } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                    return;
                }

                // In this case, the message is the "IP"
                InternalAPI.delete(message, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
            }
        }
    }
}
