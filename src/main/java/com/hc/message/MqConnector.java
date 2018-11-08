package com.hc.message;

import com.google.gson.Gson;
import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.configuration.CommonConfig;
import com.hc.configuration.MqConfig;
import com.hc.dispatch.CallbackManager;
import com.hc.dispatch.ClusterManager;
import com.hc.dispatch.event.EventHandler;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Slf4j
@LoadOrder(value = 5)
public class MqConnector implements Bootstrap {
    @Resource
    private MqConfig mqConfig;
    @Resource
    private CommonConfig commonConfig;
    @Resource
    private Gson gson;
    @Resource
    private ClusterManager clusterManager;
    @Resource
    private CallbackManager callbackManager;
    private static Connection connection;
    private static String QUEUE_MODEL = "direct";

    @SneakyThrows
    private void connect() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(mqConfig.getMqHost());
        factory.setPort(mqConfig.getMqPort());
        factory.setUsername(mqConfig.getMqUserName());
        factory.setPassword(mqConfig.getMqPwd());
        factory.setVirtualHost(StringUtils.isBlank(mqConfig.getVirtualHost()) ? "/" : mqConfig.getVirtualHost());
        connection = factory.newConnection();
    }

    private void consume() {
        Channel channel;
        String upQueueName = mqConfig.getUpQueueName();
        String exchangeName = mqConfig.getExchangeName();
        try {
            //TODO 连接池？
            channel = connection.createChannel();
            channel.exchangeDeclare(exchangeName, QUEUE_MODEL, false);
            channel.queueDeclare(upQueueName, true, false, false, null);
            channel.queueBind(upQueueName, exchangeName, "");
            channel.basicConsume(upQueueName, true, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");
                    log.info("收到消息：{}", message);
                    log.info(consumerTag);
                    Object dispatcherId;
                    if ((dispatcherId = properties.getHeaders().get("dispatcherId")) != null) {
                        clusterManager.publish((String) dispatcherId, message);
                    }

                }
            });
        } catch (IOException e) {
            log.error("rabbitmq连接失败，{}", e);
        }
    }

    /**
     * 与dispatcher同步通信
     * 同步发送消息需要注册一个eventHandler事件处理器，继承SyncEventHandler，并通过setEventType添加事件类型（枚举中定义）
     * 这个eventHandler将作为异步——同步的桥梁传递事件，dispatcher端响应对的结果将通过eventHandler同步返回
     * 若不注册eventHandler，则会导致无法接收到事件，同步调用超时，添加新的eventHandler后注意要通过
     * 回调流程详见 {@link EventHandler}
     *
     * @param serialNumber 流水号
     * @param message      消息
     * @return TransportEventEntry
     */
    public TransportEventEntry publishSync(String downQueueName, String serialNumber, String message) {
        return this.publishSync(downQueueName, serialNumber, message, null);
    }

    /**
     * 与connector同步通信
     * 同步发送消息需要注册一个eventHandler事件处理器，继承SyncEventHandler，并通过setEventType添加事件类型（枚举中定义）
     * 这个eventHandler将作为异步——同步的桥梁传递事件，dispatcher端响应对的结果将通过eventHandler同步返回
     * 若不注册eventHandler，则会导致无法接收到事件，同步调用超时
     * 回调流程详见 {@link EventHandler}
     *
     * @param downQueueName 设备队列名
     * @param message       消息
     * @param serialNumber  消息流水号
     * @return connector端返回事件
     */
    public TransportEventEntry publishSync(String downQueueName,
                                           String message,
                                           String serialNumber,
                                           Map<String, Object> headers) {
        SyncWarpper warpper = new SyncWarpper();
        Consumer<TransportEventEntry> consumerProxy = warpper.mockCallback();
        callbackManager.registerCallbackEvent(serialNumber, consumerProxy);
        publish(downQueueName, message, headers);
        return warpper.blockingResult();
    }

    /**
     * 异步通信
     *
     * @param message 消息
     */
    public void publish(String downQueueName, String message) {
        this.publish(downQueueName, message, null);
    }

    /**
     * 向dispatcher异步推送消息
     *
     * @param message       消息体
     * @param downQueueName 设备队列名
     */
    public void publish(String downQueueName, String message, Map<String, Object> headers) {
        String exchangeName = mqConfig.getExchangeName();
        try {
            //建立通道
            Channel channel = connection.createChannel();
            //交换机持久化
            channel.exchangeDeclare(exchangeName, QUEUE_MODEL, false);
            channel.queueBind(downQueueName, exchangeName, "");
            //设置数据持久化
            AMQP.BasicProperties props = new AMQP.BasicProperties().
                    builder().
                    deliveryMode(2).
                    headers(headers).
                    build();
            channel.basicPublish(exchangeName, "", props, message.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init() {
        connect();
        consume();
    }

    /**
     * 消息同步器
     */
    private class SyncWarpper {
        private volatile TransportEventEntry eventEntry;
        private CountDownLatch latch = new CountDownLatch(1);
        private long current = System.currentTimeMillis();

        public TransportEventEntry blockingResult() {
            try {
                boolean await = latch.await(commonConfig.getMaxBusBlockingTime(), TimeUnit.MILLISECONDS);
                if (!await) {
                    log.warn("同步调用超时，检查mq连接状态");
                    return new TransportEventEntry();
                }
            } catch (InterruptedException e) {
                log.warn("同步调用线程被中断,{}", e);
                return new TransportEventEntry();
            }
            log.info("同步调用返回结果:{},耗时：{}", eventEntry, System.currentTimeMillis() - current);
            return eventEntry;
        }

        public Consumer<TransportEventEntry> mockCallback() {
            return eventEntry -> {
                this.eventEntry = eventEntry;
                latch.countDown();
            };
        }
    }

    private static final String QUEUE_PREFIX = "connector_";

    public String getDownQueueName(String equipmentName) {
        return QUEUE_PREFIX + equipmentName;
    }
}
