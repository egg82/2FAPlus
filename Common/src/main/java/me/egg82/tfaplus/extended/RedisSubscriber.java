package me.egg82.tfaplus.extended;

import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.HOTPData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.core.TOTPData;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.services.Redis;
import me.egg82.tfaplus.utils.RedisUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.analytics.utils.JSONUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Base64;
import java.util.UUID;

public class RedisSubscriber {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static Base64.Decoder decoder = Base64.getDecoder();

    public RedisSubscriber() {
        try (Jedis redis = RedisUtil.getRedis()) {
            if (redis == null) {
                return;
            }

            redis.subscribe(new Subscriber(), "2faplus-login", "2faplus-authy", "2faplus-totp", "2faplus-delete");
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    class Subscriber extends JedisPubSub {
        private Subscriber() { super(); }

        public void onMessage(String channel, String message) {
            if (channel.equals("2faplus-login")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    String ip = (String) obj.get("ip");
                    long created = ((Number) obj.get("created")).longValue();
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

                    InternalAPI.add(new LoginData(uuid, ip, created));
                } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-authy")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    long i = ((Number) obj.get("i")).longValue();
                    UUID id = UUID.fromString((String) obj.get("id"));

                    if (id.equals(Redis.getServerID())) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                    Configuration config = ServiceLocator.get(Configuration.class);

                    InternalAPI.add(new AuthyData(uuid, i));
                } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-totp")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    long length = ((Number) obj.get("length")).longValue();
                    byte[] key = decoder.decode((String) obj.get("key"));
                    UUID id = UUID.fromString((String) obj.get("id"));

                    if (id.equals(Redis.getServerID())) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                    Configuration config = ServiceLocator.get(Configuration.class);

                    InternalAPI.add(new TOTPData(uuid, length, key));
                } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-hotp")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    long length = ((Number) obj.get("length")).longValue();
                    long counter = ((Number) obj.get("counter")).longValue();
                    byte[] key = decoder.decode((String) obj.get("key"));
                    UUID id = UUID.fromString((String) obj.get("id"));

                    if (id.equals(Redis.getServerID())) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                    Configuration config = ServiceLocator.get(Configuration.class);

                    InternalAPI.add(new HOTPData(uuid, length, counter, key));
                } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-delete")) {
                CachedConfigValues cachedConfig;
                Configuration config;

                try {
                    cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                    config = ServiceLocator.get(Configuration.class);
                } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                    logger.error(ex.getMessage(), ex);
                    return;
                }

                // In this case, the message is the "UUID"
                InternalAPI.delete(message);
            }
        }
    }
}
