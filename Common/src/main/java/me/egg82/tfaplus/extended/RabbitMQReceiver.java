package me.egg82.tfaplus.extended;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.core.AuthyData;
import me.egg82.tfaplus.core.HOTPData;
import me.egg82.tfaplus.core.LoginData;
import me.egg82.tfaplus.core.TOTPData;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.services.RabbitMQ;
import me.egg82.tfaplus.utils.RabbitMQUtil;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.analytics.utils.JSONUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQReceiver {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static Base64.Decoder decoder = Base64.getDecoder();

    private Connection connection = null;
    private Channel channel = null;

    public RabbitMQReceiver(ConnectionFactory factory) {
        try {
            connection = RabbitMQUtil.getConnection(factory);
            channel = RabbitMQUtil.getChannel(connection);

            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("2faplus-login", "fanout");
            channel.exchangeDeclare("2faplus-authy", "fanout");
            channel.exchangeDeclare("2faplus-totp", "fanout");
            channel.exchangeDeclare("2faplus-delete", "fanout");

            String loginQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(loginQueueName, "2faplus-login", "");

            String authyQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(authyQueueName, "2faplus-authy", "");

            String totpQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(totpQueueName, "2faplus-totp", "");

            String hotpQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(hotpQueueName, "2faplus-hotp", "");

            String deleteQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(deleteQueueName, "2faplus-delete", "");

            Consumer loginConsumer = new DefaultConsumer(channel) {
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

                        if (id.equals(RabbitMQ.getServerID())) {
                            logger.info("ignoring message sent from this server");
                            return;
                        }

                        CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        Configuration config = ServiceLocator.get(Configuration.class);

                        InternalAPI.add(new LoginData(uuid, ip, created), cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(loginQueueName, true, loginConsumer);

            Consumer authyConsumer = new DefaultConsumer(channel) {
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

                        if (id.equals(RabbitMQ.getServerID())) {
                            logger.info("ignoring message sent from this server");
                            return;
                        }

                        CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        Configuration config = ServiceLocator.get(Configuration.class);

                        InternalAPI.add(new AuthyData(uuid, i), cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(authyQueueName, true, authyConsumer);

            Consumer totpConsumer = new DefaultConsumer(channel) {
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

                        if (id.equals(RabbitMQ.getServerID())) {
                            logger.info("ignoring message sent from this server");
                            return;
                        }

                        CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        Configuration config = ServiceLocator.get(Configuration.class);

                        InternalAPI.add(new TOTPData(uuid, length, key), cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(totpQueueName, true, totpConsumer);

            Consumer hotpConsumer = new DefaultConsumer(channel) {
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

                        if (id.equals(RabbitMQ.getServerID())) {
                            logger.info("ignoring message sent from this server");
                            return;
                        }

                        CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        Configuration config = ServiceLocator.get(Configuration.class);

                        InternalAPI.add(new HOTPData(uuid, length, counter, key), cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(hotpQueueName, true, hotpConsumer);

            Consumer deleteConsumer = new DefaultConsumer(channel) {
                public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");

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
                    InternalAPI.delete(message, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                }
            };
            channel.basicConsume(deleteQueueName, true, deleteConsumer);
        } catch (ShutdownSignalException ignored) {

        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void close() throws IOException, TimeoutException {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (AlreadyClosedException ignored) {}
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (AlreadyClosedException ignored) {}
    }
}
