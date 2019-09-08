package me.egg82.tfaplus.messaging;

import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.ServiceKeys;
import me.egg82.tfaplus.services.AbstractRedis;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.analytics.utils.JSONUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisException;

public class Redis extends AbstractRedis implements MessageHandler {
    private static Base64.Encoder encoder = Base64.getEncoder();
    private static Base64.Decoder decoder = Base64.getDecoder();

    public Redis(JedisPool pool) { super(pool); }

    public Redis(JedisPool pool, String password) { super(pool, password); }

    public void beginHandling() {
        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            redis.subscribe(new Subscriber(),
                    "2faplus-login",
                    "2faplus-authy",
                    "2faplus-totp",
                    "2faplus-hotp",
                    "2faplus-delete"
            );
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    private class Subscriber extends JedisPubSub {
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

                    if (id.equals(handlerID)) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    InternalAPI.add(new LoginData(uuid, ip, created));
                } catch (APIException | ParseException | ClassCastException | NullPointerException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-authy")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    long i = ((Number) obj.get("i")).longValue();
                    UUID id = UUID.fromString((String) obj.get("id"));

                    if (id.equals(handlerID)) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    InternalAPI.add(new AuthyData(uuid, i));
                } catch (APIException | ParseException | ClassCastException | NullPointerException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-totp")) {
                try {
                    JSONObject obj = JSONUtil.parseObject(message);
                    UUID uuid = UUID.fromString((String) obj.get("uuid"));
                    long length = ((Number) obj.get("length")).longValue();
                    byte[] key = decoder.decode((String) obj.get("key"));
                    UUID id = UUID.fromString((String) obj.get("id"));

                    if (id.equals(handlerID)) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    InternalAPI.add(new TOTPData(uuid, length, key));
                } catch (APIException | ParseException | ClassCastException | NullPointerException ex) {
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

                    if (id.equals(handlerID)) {
                        logger.info("ignoring message sent from this server");
                        return;
                    }

                    InternalAPI.add(new HOTPData(uuid, length, counter, key));
                } catch (APIException | ParseException | ClassCastException | NullPointerException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } else if (channel.equals("2faplus-delete")) {
                // In this case, the message is the "UUID"

                if (!ValidationUtil.isValidUuid(message)) {
                    logger.warn("non-valid UUID sent through Redis pub/sub");
                    return;
                }

                try {
                    InternalAPI.deleteFromMessaging(UUID.fromString(message));
                } catch (APIException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private final UUID handlerID = UUID.randomUUID();
    public UUID getHandlerID() { return handlerID; }

    public void updateFromQueue(SQLFetchResult sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            for (String key : sqlResult.getRemovedKeys()) {
                redis.del(key);
                if (key.indexOf('|') == -1 && ValidationUtil.isValidUuid(key.substring(key.lastIndexOf(':') + 1))) {
                    redis.publish("2faplus-delete", key.substring(key.lastIndexOf(':') + 1));
                }
            }

            for (LoginData result : sqlResult.getLoginData()) {
                String key = "2faplus:login:" + result.getUUID() + "|" + result.getIP();
                int offset = (int) Math.floorDiv(System.currentTimeMillis() - result.getCreated(), 1000L);
                int cacheTime = (int) Math.floorDiv(cachedConfig.get().getIPCacheTime(), 1000L);
                if (offset < cacheTime) {
                    redis.setex(key, cacheTime - offset, String.valueOf(Boolean.TRUE));
                } else {
                    redis.del(key);
                }

                String ipKey = "2faplus:ip:" + result.getIP();
                if (offset < cacheTime) {
                    redis.sadd(ipKey, result.getUUID().toString());
                } else {
                    redis.srem(ipKey, result.getUUID().toString());
                }

                String uuidKey = "2faplus:uuid:" + result.getUUID();
                if (offset < cacheTime) {
                    redis.sadd(uuidKey, result.getIP());
                } else {
                    redis.srem(uuidKey, result.getIP());
                }

                if (offset < cacheTime) {
                    JSONObject obj = new JSONObject();
                    obj.put("uuid", result.getUUID().toString());
                    obj.put("ip", result.getIP());
                    obj.put("created", result.getCreated());
                    obj.put("id", serverId.toString());
                    redis.publish("2faplus-login", obj.toJSONString());
                } else {
                    redis.publish("2faplus-delete", result.getUUID().toString());
                }
            }

            for (AuthyData result : sqlResult.getAuthyData()) {
                String key = "2faplus:authy:" + result.getUUID();
                redis.set(key, String.valueOf(result.getID()));

                JSONObject obj = new JSONObject();
                obj.put("uuid", result.getUUID().toString());
                obj.put("i", result.getID());
                obj.put("id", serverId.toString());
                redis.publish("2faplus-authy", obj.toJSONString());
            }

            for (TOTPData result : sqlResult.getTOTPData()) {
                String key = "2faplus:totp:" + result.getUUID();

                JSONObject obj = new JSONObject();
                obj.put("length", result.getLength());
                obj.put("key", encoder.encodeToString(result.getKey().getEncoded()));
                redis.set(key, obj.toJSONString());

                obj.put("uuid", result.getUUID().toString());
                obj.put("id", serverId.toString());
                redis.publish("2faplus-totp", obj.toJSONString());
            }

            for (HOTPData result : sqlResult.getHOTPData()) {
                String key = "2faplus:hotp:" + result.getUUID();

                JSONObject obj = new JSONObject();
                obj.put("length", result.getLength());
                obj.put("counter", result.getCounter());
                obj.put("key", encoder.encodeToString(result.getKey().getEncoded()));
                redis.set(key, obj.toJSONString());

                obj.put("uuid", result.getUUID().toString());
                obj.put("id", serverId.toString());
                redis.publish("2faplus-hotp", obj.toJSONString());
            }
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(LoginData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            String key = "2faplus:login:" + sqlResult.getUUID() + "|" + sqlResult.getIP();
            int offset = (int) Math.floorDiv(System.currentTimeMillis() - sqlResult.getCreated(), 1000L);
            int cacheTime = (int) Math.floorDiv(cachedConfig.get().getIPCacheTime(), 1000L);
            if (offset < cacheTime) {
                redis.setex(key, cacheTime - offset, String.valueOf(Boolean.TRUE));
            } else {
                redis.del(key);
            }

            String ipKey = "2faplus:ip:" + sqlResult.getIP();
            if (offset < cacheTime) {
                redis.sadd(ipKey, sqlResult.getUUID().toString());
            } else {
                redis.srem(ipKey, sqlResult.getUUID().toString());
            }

            String uuidKey = "2faplus:uuid:" + sqlResult.getUUID();
            if (offset < cacheTime) {
                redis.sadd(uuidKey, sqlResult.getIP());
            } else {
                redis.srem(uuidKey, sqlResult.getIP());
            }

            if (offset < cacheTime) {
                JSONObject obj = new JSONObject();
                obj.put("uuid", sqlResult.getUUID().toString());
                obj.put("ip", sqlResult.getIP());
                obj.put("created", sqlResult.getCreated());
                obj.put("id", serverId.toString());
                redis.publish("2faplus-login", obj.toJSONString());
            } else {
                redis.publish("2faplus-delete", sqlResult.getUUID().toString());
            }
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(AuthyData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            String key = "2faplus:authy:" + sqlResult.getUUID();
            redis.set(key, String.valueOf(sqlResult.getID()));

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("i", sqlResult.getID());
            obj.put("id", serverId.toString());
            redis.publish("2faplus-authy", obj.toJSONString());
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(TOTPData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            String key = "2faplus:totp:" + sqlResult.getUUID();

            JSONObject obj = new JSONObject();
            obj.put("length", sqlResult.getLength());
            obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
            redis.set(key, obj.toJSONString());

            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("id", serverId.toString());
            redis.publish("2faplus-totp", obj.toJSONString());
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(HOTPData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            String key = "2faplus:hotp:" + sqlResult.getUUID();

            JSONObject obj = new JSONObject();
            obj.put("length", sqlResult.getLength());
            obj.put("counter", sqlResult.getCounter());
            obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
            redis.set(key, obj.toJSONString());

            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("id", serverId.toString());
            redis.publish("2faplus-hotp", obj.toJSONString());
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void delete(String ip) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            String ipKey = "2faplus:ip:" + ip;

            Set<String> data = redis.smembers(ipKey);
            if (data != null) {
                for (String uuid : data) {
                    String uuidKey = "2faplus:uuid:" + uuid;
                    String loginKey = "2faplus:login:" + uuid + "|" + ip;
                    String authyKey = "2faplus:authy:" + uuid;
                    String totpKey = "2faplus:totp:" + uuid;
                    String hotpKey = "2faplus:hotp:" + uuid;
                    redis.del(loginKey);
                    redis.del(authyKey);
                    redis.del(totpKey);
                    redis.del(hotpKey);
                    redis.srem(uuidKey, ip);

                    redis.publish("2faplus-delete", uuid);
                }
            }
            redis.del(ipKey);
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void delete(UUID uuid) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Jedis redis = getRedis()) {
            if (redis == null) {
                return;
            }

            String uuidKey = "2faplus:uuid:" + uuid;

            Set<String> data = redis.smembers(uuidKey);
            if (data != null) {
                for (String ip : data) {
                    String ipKey = "2faplus:ip:" + ip;
                    String loginKey = "2faplus:login:" + uuid + "|" + ip;
                    redis.del(loginKey);
                    redis.srem(ipKey, uuid.toString());
                }
            }
            redis.del(uuidKey);

            String authyKey = "2faplus:authy:" + uuid;
            String totpKey = "2faplus:totp:" + uuid;
            String hotpKey = "2faplus:hotp:" + uuid;
            redis.del(authyKey);
            redis.del(totpKey);
            redis.del(hotpKey);

            redis.publish("2faplus-delete", uuid.toString());
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public Optional<Boolean> getLogin(UUID uuid, String ip) {
        Boolean result = null;

        try (Jedis redis = getRedis()) {
            if (redis != null) {
                String key = "2faplus:login:" + uuid + "|" + ip;

                // Grab info
                String data = redis.get(key);
                if (data != null) {
                    result = Boolean.valueOf(data);
                }
            }
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return Optional.ofNullable(result);
    }

    public Optional<Long> getAuthy(UUID uuid) {
        Long result = null;

        try (Jedis redis = getRedis()) {
            if (redis != null) {
                String key = "2faplus:authy:" + uuid;

                // Grab info
                String data = redis.get(key);
                if (data != null) {
                    result = Long.valueOf(data);
                }
            }
        } catch (JedisException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return Optional.ofNullable(result);
    }

    public Optional<TOTPData> getTOTP(UUID uuid) {
        TOTPData result = null;

        try (Jedis redis = getRedis()) {
            if (redis != null) {
                String key = "2faplus:totp:" + uuid;

                // Grab info
                String data = redis.get(key);
                if (data != null) {
                    JSONObject obj = JSONUtil.parseObject(data);
                    result = new TOTPData(uuid, ((Number) obj.get("length")).longValue(), new SecretKeySpec(decoder.decode((String) obj.get("key")), ServiceKeys.TOTP_ALGORITM));
                }
            }
        } catch (JedisException | ParseException | ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return Optional.ofNullable(result);
    }

    public Optional<HOTPData> getHOTP(UUID uuid) {
        HOTPData result = null;

        try (Jedis redis = getRedis()) {
            if (redis != null) {
                String key = "2faplus:hotp:" + uuid;

                // Grab info
                String data = redis.get(key);
                if (data != null) {
                    JSONObject obj = JSONUtil.parseObject(data);
                    result = new HOTPData(uuid, ((Number) obj.get("length")).longValue(), ((Number) obj.get("counter")).longValue(), new SecretKeySpec(decoder.decode((String) obj.get("key")), ServiceKeys.HOTP_ALGORITM));
                }
            }
        } catch (JedisException | ParseException | ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return Optional.ofNullable(result);
    }
}
