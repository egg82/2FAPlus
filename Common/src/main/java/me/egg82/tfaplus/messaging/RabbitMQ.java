package me.egg82.tfaplus.messaging;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.auth.data.AuthyData;
import me.egg82.tfaplus.auth.data.HOTPData;
import me.egg82.tfaplus.auth.data.TOTPData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.utils.ConfigUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.analytics.utils.JSONUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQ implements MessageHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Base64.Encoder encoder = Base64.getEncoder();
    private final Base64.Decoder decoder = Base64.getDecoder();

    private final ConnectionFactory connectionFactory;
    private Connection listenConnection = null;
    private Channel listenChannel = null;

    public RabbitMQ(ConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("connectionFactory cannot be null.");
        }

        this.connectionFactory = connectionFactory;
    }

    public void beginHandling() {
        try {
            listenConnection = getConnection();
            listenChannel = getChannel(listenConnection);
            if (listenConnection == null || listenChannel == null) {
                return;
            }

            handleQueue("2faplus-login",
                    new DefaultConsumer(listenChannel) {
                        public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                            String message = new String(body, "UTF-8");

                            try {
                                JSONObject obj = JSONUtil.parseObject(message);

                                if (!ValidationUtil.isValidUuid((String) obj.get("uuid"))) {
                                    logger.warn("non-valid UUID sent through RabbitMQ");
                                    return;
                                }

                                UUID uuid = UUID.fromString((String) obj.get("uuid"));
                                String ip = (String) obj.get("ip");
                                long created = ((Number) obj.get("created")).longValue();
                                UUID id = UUID.fromString((String) obj.get("id"));

                                if (!ValidationUtil.isValidIp(ip)) {
                                    logger.warn("non-valid IP sent through RabbitMQ");
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
                        }
                    }
            );

            handleQueue("2faplus-authy",
                    new DefaultConsumer(listenChannel) {
                        public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                            String message = new String(body, "UTF-8");

                            try {
                                JSONObject obj = JSONUtil.parseObject(message);

                                if (!ValidationUtil.isValidUuid((String) obj.get("uuid"))) {
                                    logger.warn("non-valid UUID sent through RabbitMQ");
                                    return;
                                }

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
                        }
                    }
            );

            handleQueue("2faplus-totp",
                    new DefaultConsumer(listenChannel) {
                        public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                            String message = new String(body, "UTF-8");

                            try {
                                JSONObject obj = JSONUtil.parseObject(message);

                                if (!ValidationUtil.isValidUuid((String) obj.get("uuid"))) {
                                    logger.warn("non-valid UUID sent through RabbitMQ");
                                    return;
                                }

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
                        }
                    }
            );

            handleQueue("2faplus-hotp",
                    new DefaultConsumer(listenChannel) {
                        public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                            String message = new String(body, "UTF-8");

                            try {
                                JSONObject obj = JSONUtil.parseObject(message);

                                if (!ValidationUtil.isValidUuid((String) obj.get("uuid"))) {
                                    logger.warn("non-valid UUID sent through RabbitMQ");
                                    return;
                                }

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
                        }
                    }
            );

            handleQueue("2faplus-delete",
                    new DefaultConsumer(listenChannel) {
                        public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                            String message = new String(body, "UTF-8");

                            if (!ValidationUtil.isValidUuid(message)) {
                                logger.warn("non-valid UUID sent through RabbitMQ");
                                return;
                            }

                            // In this case, the message is the "UUID"
                            try {
                                InternalAPI.deleteFromMessaging(UUID.fromString(message));
                            } catch (APIException ex) {
                                logger.error(ex.getMessage(), ex);
                            }
                        }
                    }
            );
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void close() {
        try {
            if (listenChannel != null) {
                listenChannel.close();
            }
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (AlreadyClosedException ignored) {}

        try {
            if (listenConnection != null) {
                listenConnection.close();
            }
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (AlreadyClosedException ignored) {}
    }

    private final UUID handlerID = UUID.randomUUID();
    public UUID getHandlerID() { return handlerID; }

    public void broadcast(LoginData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = getChannel(getConnection())) {
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
                obj.put("id", handlerID.toString());

                channel.exchangeDeclare("2faplus-login", "fanout");
                channel.basicPublish("2faplus-login", "", null, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
            } else {
                channel.exchangeDeclare("2faplus-delete", "fanout");
                channel.basicPublish("2faplus-delete", "", null, sqlResult.getUUID().toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(AuthyData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = getChannel(getConnection())) {
            if (channel == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("i", sqlResult.getID());
            obj.put("id", handlerID.toString());

            channel.exchangeDeclare("2faplus-authy", "fanout");
            channel.basicPublish("2faplus-authy", "", null, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(TOTPData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = getChannel(getConnection())) {
            if (channel == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("length", sqlResult.getLength());
            obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
            obj.put("id", handlerID.toString());

            channel.exchangeDeclare("2faplus-totp", "fanout");
            channel.basicPublish("2faplus-totp", "", null, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void broadcast(HOTPData sqlResult) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = getChannel(getConnection())) {
            if (channel == null) {
                return;
            }

            JSONObject obj = new JSONObject();
            obj.put("uuid", sqlResult.getUUID().toString());
            obj.put("length", sqlResult.getLength());
            obj.put("counter", sqlResult.getCounter());
            obj.put("key", encoder.encodeToString(sqlResult.getKey().getEncoded()));
            obj.put("id", handlerID.toString());

            channel.exchangeDeclare("2faplus-hotp", "fanout");
            channel.basicPublish("2faplus-hotp", "", null, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void delete(UUID uuid) {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        try (Channel channel = getChannel(getConnection())) {
            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("2faplus-delete", "fanout");
            channel.basicPublish("2faplus-delete", "", null, uuid.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    private void handleQueue(String name, Consumer consumer) throws IOException {
        listenChannel.exchangeDeclare(name, "fanout");
        String queueName = listenChannel.queueDeclare().getQueue();
        listenChannel.queueBind(queueName, name, "");
        listenChannel.basicConsume(queueName, true, consumer);
    }

    private Connection getConnection() throws IOException, TimeoutException {
        Connection connection = null;
        if (connectionFactory != null) {
            connection = connectionFactory.newConnection();
        }
        return connection;
    }

    private Channel getChannel(Connection connection) throws IOException {
        Channel retVal = null;
        if (connection != null) {
            retVal = connection.createChannel();
        }
        return retVal;
    }
}
