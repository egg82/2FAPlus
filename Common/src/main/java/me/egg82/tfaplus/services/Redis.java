package me.egg82.tfaplus.services;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.core.SQLFetchResult;
import me.egg82.tfaplus.core.TOTPData;
import me.egg82.tfaplus.extended.ServiceKeys;
import me.egg82.tfaplus.utils.RedisUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.analytics.utils.JSONUtil;
import ninja.egg82.tuples.longs.LongObjectPair;
import ninja.leaping.configurate.ConfigurationNode;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class Redis {
    private static final Logger logger = LoggerFactory.getLogger(Redis.class);

    private static Base64.Encoder encoder = Base64.getEncoder();
    private static Base64.Decoder decoder = Base64.getDecoder();

    private static final UUID serverId = UUID.randomUUID();
    public static UUID getServerID() { return serverId; }

    private Redis() {}

    public static CompletableFuture<Boolean> updateFromQueue(SQLFetchResult sqlResult, long ipTime, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                for (String key : sqlResult.getRemovedKeys()) {
                    redis.del(key);
                    if (key.indexOf('|') == -1 && ValidationUtil.isValidUuid(key.substring(key.lastIndexOf(':') + 1))) {
                        redis.publish("2faplus-delete", key.substring(key.lastIndexOf(':') + 1));
                    }
                }

                for (LoginData result : sqlResult.getLoginData()) {
                    String key = "2faplus:login:" + result.getUUID() + "|" + result.getIP();
                    int offset = (int) Math.floorDiv((ipTime + result.getCreated()) - System.currentTimeMillis(), 1000L);
                    if (offset > 0) {
                        redis.setex(key, offset, String.valueOf(Boolean.TRUE));
                    } else {
                        redis.del(key);
                    }

                    String ipKey = "2faplus:ip:" + result.getIP();
                    if (offset > 0) {
                        redis.sadd(ipKey, result.getUUID().toString());
                    } else {
                        redis.srem(ipKey, result.getUUID().toString());
                    }

                    String uuidKey = "2faplus:uuid:" + result.getUUID();
                    if (offset > 0) {
                        redis.sadd(uuidKey, result.getIP());
                    } else {
                        redis.srem(uuidKey, result.getIP());
                    }

                    if (offset > 0) {
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

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> update(LoginData sqlResult, long ipTime, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String key = "2faplus:login:" + sqlResult.getUUID() + "|" + sqlResult.getIP();
                int offset = (int) Math.floorDiv((ipTime + sqlResult.getCreated()) - System.currentTimeMillis(), 1000L);
                if (offset > 0) {
                    redis.setex(key, offset, String.valueOf(Boolean.TRUE));
                } else {
                    redis.del(key);
                }

                String ipKey = "2faplus:ip:" + sqlResult.getIP();
                if (offset > 0) {
                    redis.sadd(ipKey, sqlResult.getUUID().toString());
                } else {
                    redis.srem(ipKey, sqlResult.getUUID().toString());
                }

                String uuidKey = "2faplus:uuid:" + sqlResult.getUUID();
                if (offset > 0) {
                    redis.sadd(uuidKey, sqlResult.getIP());
                } else {
                    redis.srem(uuidKey, sqlResult.getIP());
                }

                if (offset > 0) {
                    JSONObject obj = new JSONObject();
                    obj.put("uuid", sqlResult.getUUID().toString());
                    obj.put("ip", sqlResult.getIP());
                    obj.put("created", sqlResult.getCreated());
                    obj.put("id", serverId.toString());
                    redis.publish("2faplus-login", obj.toJSONString());
                } else {
                    redis.publish("2faplus-delete", sqlResult.getUUID().toString());
                }

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> update(AuthyData sqlResult, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String key = "2faplus:authy:" + sqlResult.getUUID();
                redis.set(key, String.valueOf(sqlResult.getID()));

                JSONObject obj = new JSONObject();
                obj.put("uuid", sqlResult.getUUID().toString());
                obj.put("i", sqlResult.getID());
                obj.put("id", serverId.toString());
                redis.publish("2faplus-authy", obj.toJSONString());

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> update(TOTPData sqlResult, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String key = "2faplus:totp:" + sqlResult.getUUID();

                JSONObject obj = new JSONObject();
                obj.put("length", sqlResult.getLength());
                obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
                redis.set(key, obj.toJSONString());

                obj.put("uuid", sqlResult.getUUID().toString());
                obj.put("id", serverId.toString());
                redis.publish("2faplus-totp", obj.toJSONString());

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(String ip, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String ipKey = "2faplus:ip:" + ip;

                Set<String> data = redis.smembers(ipKey);
                if (data != null) {
                    for (String uuid : data) {
                        String uuidKey = "2faplus:uuid:" + uuid;
                        String loginKey = "2faplus:login:" + uuid + "|" + ip;
                        String authyKey = "2faplus:authy:" + uuid;
                        String totpKey = "2faplus:totp:" + uuid;
                        redis.del(loginKey);
                        redis.del(authyKey);
                        redis.del(totpKey);
                        redis.srem(uuidKey, ip);

                        redis.publish("2faplus-delete", uuid);
                    }
                }
                redis.del(ipKey);

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(UUID uuid, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
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
                redis.del(authyKey);
                redis.del(totpKey);

                redis.publish("2faplus-delete", uuid.toString());

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> getLogin(UUID uuid, String ip, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            Boolean result = null;

            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
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

            return result;
        });
    }

    public static CompletableFuture<Long> getAuthy(UUID uuid, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            Long result = null;

            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
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

            return result;
        });
    }

    public static CompletableFuture<LongObjectPair<SecretKey>> getTOTP(UUID uuid, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            LongObjectPair<SecretKey> result = null;

            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis != null) {
                    String key = "2faplus:totp:" + uuid;

                    // Grab info
                    String data = redis.get(key);
                    if (data != null) {
                        JSONObject obj = JSONUtil.parseObject(data);
                        result = new LongObjectPair<>(((Number) obj.get("length")).longValue(), new SecretKeySpec(decoder.decode((String) obj.get("key")), ServiceKeys.TOTP_ALGORITM));
                    }
                }
            } catch (JedisException | ParseException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }
}
