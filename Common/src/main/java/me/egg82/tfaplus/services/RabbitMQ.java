package me.egg82.tfaplus.services;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.core.TOTPData;
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

    public static CompletableFuture<Boolean> broadcast(LoginData sqlResult, long ipTime, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                int offset = (int) Math.floorDiv((ipTime + sqlResult.getCreated()) - System.currentTimeMillis(), 1000L);

                if (offset > 0) {
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

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> broadcast(AuthyData sqlResult, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                JSONObject obj = new JSONObject();
                obj.put("uuid", sqlResult.getUUID().toString());
                obj.put("i", sqlResult.getID());
                obj.put("id", serverId.toString());

                channel.exchangeDeclare("2faplus-authy", "fanout");
                channel.basicPublish("2faplus-authy", "", null, obj.toJSONString().getBytes(utf8));

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> broadcast(TOTPData sqlResult, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                JSONObject obj = new JSONObject();
                obj.put("uuid", sqlResult.getUUID().toString());
                obj.put("length", sqlResult.getLength());
                obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
                obj.put("id", serverId.toString());

                channel.exchangeDeclare("2faplus-totp", "fanout");
                channel.basicPublish("2faplus-totp", "", null, obj.toJSONString().getBytes(utf8));

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(UUID uuid, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                channel.exchangeDeclare("2faplus-delete", "fanout");
                channel.basicPublish("2faplus-delete", "", null, uuid.toString().getBytes(utf8));

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }
}
