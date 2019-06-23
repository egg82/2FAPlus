package me.egg82.tfaplus.services;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.HOTPData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.core.TOTPData;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.RabbitMQUtil;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQ {
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQ.class);

    private static Base64.Encoder encoder = Base64.getEncoder();

    private static final UUID serverId = UUID.randomUUID();
    public static UUID getServerID() { return serverId; }

    private static Charset utf8 = Charset.forName("UTF-8");

    private RabbitMQ() {}

    public static void broadcast(LoginData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            int offset = (int) Math.floorDiv(System.currentTimeMillis() - sqlResult.getCreated(), 1000L);
            int cacheTime = (int) Math.floorDiv(cachedConfig.get().getIPCacheTime(), 1000L);

            if (offset < cacheTime) {
                JSONObject obj = new JSONObject();
                obj.put("uuid", sqlResult.getUUID().toString());
                obj.put("ip", sqlResult.getIP());
                obj.put("created", sqlResult.getCreated());
                obj.put("id", serverId.toString());

                channel.exchangeDeclare("2faplus-login", "fanout");
                channel.basicPublish("2faplus-login", "", null, obj.toJSONString().getBytes(utf8));
            } else {
                channel.exchangeDeclare("2faplus-delete", "fanout");
                channel.basicPublish("2faplus-delete", "", null, sqlResult.getUUID().toString().getBytes(utf8));
            }
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void broadcast(AuthyData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("i", sqlResult.getID());
            obj.put("id", serverId.toString());

            channel.exchangeDeclare("2faplus-authy", "fanout");
            channel.basicPublish("2faplus-authy", "", null, obj.toJSONString().getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void broadcast(TOTPData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("length", sqlResult.getLength());
            obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
            obj.put("id", serverId.toString());

            channel.exchangeDeclare("2faplus-totp", "fanout");
            channel.basicPublish("2faplus-totp", "", null, obj.toJSONString().getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void broadcast(HOTPData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("length", sqlResult.getLength());
            obj.put("counter", sqlResult.getCounter());
            obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
            obj.put("id", serverId.toString());

            channel.exchangeDeclare("2faplus-hotp", "fanout");
            channel.basicPublish("2faplus-hotp", "", null, obj.toJSONString().getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void delete(UUID uuid) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("2faplus-delete", "fanout");
            channel.basicPublish("2faplus-delete", "", null, uuid.toString().getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}
