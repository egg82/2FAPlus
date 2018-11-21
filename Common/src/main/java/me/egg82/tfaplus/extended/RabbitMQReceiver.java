package me.egg82.tfaplus.extended;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import me.egg82.tfaplus.utils.RabbitMQUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQReceiver {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Connection connection = null;
    private Channel channel = null;

    public RabbitMQReceiver(ConnectionFactory factory) {
        try {
            connection = RabbitMQUtil.getConnection(factory);
            channel = RabbitMQUtil.getChannel(connection);

            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("altfndr-info", "fanout");
            channel.exchangeDeclare("altfndr-delete", "fanout");

            String infoQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(infoQueueName, "altfndr-info", "");

            String deleteQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(deleteQueueName, "altfndr-delete", "");

            Consumer infoConsumer = new DefaultConsumer(channel) {
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
                        long count = ((Number) obj.get("count")).longValue();
                        String server = (String) obj.get("server");
                        long created = ((Number) obj.get("created")).longValue();
                        long updated = ((Number) obj.get("updated")).longValue();
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

                        InternalAPI.add(new PlayerData(uuid, ip, count, server, created, updated), cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(infoQueueName, true, infoConsumer);

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

                    // In this case, the message is the "IP" or "UUID"
                    InternalAPI.delete(message, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                }
            };
            channel.basicConsume(deleteQueueName, true, deleteConsumer);
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void close() throws IOException, TimeoutException {
        if (channel != null) {
            channel.close();
        }
        if (connection != null) {
            connection.close();
        }
    }
}
