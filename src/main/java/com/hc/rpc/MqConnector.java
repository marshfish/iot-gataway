package com.hc.rpc;

import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.configuration.CommonConfig;
import com.hc.configuration.MqConfig;
import com.hc.dispatch.CallbackManager;
import com.hc.dispatch.ClusterManager;
import com.hc.dispatch.MqEventUpStream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private ClusterManager clusterManager;
    @Resource
    private CallbackManager callbackManager;
    private Queue<PublishEvent> publishQueue = new ArrayBlockingQueue<>(100);
    private Thread publisher;
    private ExecutorService publisherFactory = Executors.newFixedThreadPool(1, r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("publish-exec-1");
        return thread;
    });
    private static Connection connection;
    private static String QUEUE_MODEL = "direct";
    private Object lock = new Object();
    private static final String EQUIPMENT_QUEUE = "equipment_type_";
    public static final String DISPATCHER_ID = "dispatcherId";
    public static final String CONNECTOR_ID = "connectorId";
    private static String routingKey;

    @SneakyThrows
    private void connectRabbitMq() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(mqConfig.getMqHost());
        factory.setPort(mqConfig.getMqPort());
        factory.setUsername(mqConfig.getMqUserName());
        factory.setPassword(mqConfig.getMqPwd());
        factory.setVirtualHost(StringUtils.isBlank(mqConfig.getVirtualHost()) ? "/" : mqConfig.getVirtualHost());
        connection = factory.newConnection();
    }

    private void registryConsumer() {
        String upQueueName = mqConfig.getUpQueueName();
        try {
            Channel channel = newChannel(upQueueName);
            channel.basicConsume(upQueueName, true, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                           AMQP.BasicProperties properties, byte[] body) throws IOException {
                    log.info("收到消息：{}", body);
                    Object dispatcherId;
                    if ((dispatcherId = properties.getHeaders().get(DISPATCHER_ID)) != null) {
                        clusterManager.publish(dispatcherId.toString(), body);
                    } else {
                        //若未指定dispatcherId，则使用当前节点解析
                        clusterManager.publish(commonConfig.getDispatcherId(), body);
                    }
                }
            });
        } catch (IOException e) {
            log.error("rabbitmq连接失败，{}", e);
        }
    }


    /**
     * 创建设备队列名
     *
     * @return Stirng
     */
    public String getRoutingKey(Integer eqType) {
        if (routingKey == null) {
            routingKey = EQUIPMENT_QUEUE + eqType;
        }
        return routingKey;
    }

    /**
     * 与dispatcher同步通信
     * 同步发送消息需要注册一个eventHandler事件处理器，继承SyncEventHandler，并通过setEventType添加事件类型（枚举中定义）
     * 这个eventHandler将作为异步——同步的桥梁传递事件，dispatcher端响应对的结果将通过eventHandler同步返回
     * 若不注册eventHandler，则会导致无法接收到事件，同步调用超时
     * 回调流程详见 {@link EventHandler}
     *
     * @param serialNumber  流水号
     * @param message       消息
     * @param downQueueName 设备队列名
     * @return connector端返回事件
     */
    public TransportEventEntry publishSync(String downQueueName, String serialNumber, byte[] message) {
        return this.publishSync(downQueueName, message, serialNumber, null);
    }

    /**
     * 与connector同步通信
     */
    public TransportEventEntry publishSync(String downQueueName,
                                           byte[] message,
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
     * @param downQueueName 设备队列名
     * @param message       消息
     */
    public void publish(String downQueueName, byte[] message) {
        publish(downQueueName, message, null);
    }

    /**
     * 向dispatcher异步推送消息
     *
     * @param message       消息体
     * @param downQueueName 设备队列名
     */
    public void publish(String downQueueName, byte[] message, Map<String, Object> headers) {
        PublishEvent event = new PublishEvent(downQueueName, message, headers);
        synchronized (lock) {
            if (publishQueue.offer(event)) {
                lock.notify();
            } else {
                log.warn("发送消息队列已满，检查publisher线程是否存活");
            }
        }
    }


    /**
     * channel并非线程安全，共用一个channel可能导致autoACK出现问题
     *
     * @return Channel
     */
    @SneakyThrows
    private Channel newChannel(String routingKey) {
        Channel channel = connection.createChannel();
        String exchangeName = mqConfig.getExchangeName();
        //direct模式、持久化交换机
        channel.exchangeDeclare(exchangeName, QUEUE_MODEL, true);
        //声明队列,持久化、非排他、非自动删除队列
        channel.queueDeclare(routingKey, true, false, false, null);
        //绑定队列到交换机
        channel.queueBind(routingKey, exchangeName, routingKey);
        return channel;
    }

    @Override
    public void init() {
        connectRabbitMq();
        registryConsumer();
        startPublishThread();
    }

    /**
     * 由于channel非线程安全，把所有publish的操作放到一条线程处理
     */
    private void startPublishThread() {
        publisher = new Thread(new Runnable() {
            private Map<String, Channel> routingChannel = new HashMap<>();
            private Logger log = LoggerFactory.getLogger(MqConnector.class);

            @Override
            public void run() {
                while (true) {
                    PublishEvent eventEntry;
                    synchronized (lock) {
                        while ((eventEntry = publishQueue.poll()) == null) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    log.info("获取消息，推送给rabbitmq");
                    //路由键与队列名相同
                    String routingKey = eventEntry.getDownQueueName();
                    byte[] message = eventEntry.getMessage();
                    Map<String, Object> headers = eventEntry.getHeaders();
                    Channel channel;
                    if ((channel = routingChannel.get(routingKey)) != null) {
                        publish(routingKey, message, headers, channel);
                    } else {
                        Channel newChannel = newChannel(routingKey);
                        routingChannel.put(routingKey, newChannel);
                        publish(routingKey, message, headers, newChannel);
                    }
                }
            }

            /**
             * 推送给mq
             *
             * @param downQueueName 下行队列名，与routingKey相同
             * @param message       消息
             * @param headers       消息头
             * @param localChannel  某一routingKey对应的channel
             */
            private void publish(String downQueueName, byte[] message, Map<String, Object> headers, Channel localChannel) {
                String exchangeName = mqConfig.getExchangeName();
                try {
                    //暂不持久化消息
                    AMQP.BasicProperties props = new AMQP.BasicProperties().
                            builder().
                            deliveryMode(1).
                            headers(headers).
                            build();
                    localChannel.basicPublish(exchangeName, downQueueName, props, message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        publisherFactory.execute(publisher);
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
}
