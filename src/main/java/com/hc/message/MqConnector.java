package com.hc.message;

import com.google.gson.Gson;
import com.hc.dispatch.CallbackManager;
import com.hc.dispatch.MqEventUpStream;
import com.hc.configuration.MqConfig;
import com.hc.dispatch.event.EventHandler;
import com.hc.util.IdGenerator;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Slf4j
public class MqConnector implements InitializingBean {
    @Resource
    private MqConfig config;
    @Resource
    private Gson gson;
    @Resource
    private MqEventUpStream mqEventUpStream;
    @Resource
    private CallbackManager callbackManager;
    private static Connection connection;
    private static String QUEUE_MODEL = "direct";

    @SneakyThrows
    private void connect() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(config.getMqHost());
        factory.setPort(config.getMqPort());
        factory.setUsername(config.getMqUserName());
        factory.setPassword(config.getMqPwd());
        factory.setVirtualHost(StringUtils.isBlank(config.getVirtualHost()) ? "/" : config.getVirtualHost());
        connection = factory.newConnection();
    }

    private void consume() {
        Channel channel;
        String upQueueName = config.getUpQueueName();
        String exchangeName = config.getExchangeName();
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
                    TransportEventEntry transportEventEntry = gson.fromJson(message, TransportEventEntry.class);
                    mqEventUpStream.handlerMessage(transportEventEntry);
                }
            });
        } catch (IOException e) {
            log.error("rabbitmq连接失败，{}", e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        connect();
        consume();
    }


    /**
     * 与connector同步通信
     * 同步发送消息需要注册一个eventHandler事件处理器，继承SyncEventHandler，并通过setEventType添加事件类型（枚举中定义）
     * 这个eventHandler将作为异步——同步的桥梁传递事件，dispatcher端响应对的结果将通过eventHandler同步返回
     * 若不注册eventHandler，则会导致无法接收到事件，同步调用超时
     * 回调流程详见 {@link EventHandler}
     * @param downQueueName 设备队列名
     * @param message      消息
     * @param serialNumber 消息流水号
     * @return connector端返回事件
     */
    public TransportEventEntry publishSync(String downQueueName, String message, String serialNumber) {
        SyncWarpper warpper = new SyncWarpper();
        Consumer<TransportEventEntry> consumerProxy = warpper.mockCallback();
        callbackManager.registerCallbackEvent(serialNumber, consumerProxy);
        publish(message, downQueueName);
        return warpper.blockingResult();
    }


    /**
     * 向dispatcher异步推送消息
     *
     * @param message      消息体
     * @param downQueueName 设备队列名
     */
    public void publish(String message,String downQueueName) {
        String exchangeName = config.getExchangeName();
        try {
            //建立通道
            Channel channel = connection.createChannel();
            //交换机持久化
            channel.exchangeDeclare(exchangeName, QUEUE_MODEL, false);
            channel.queueBind(downQueueName, exchangeName, "");
            //设置数据持久化
            AMQP.BasicProperties props = new AMQP.BasicProperties().builder().deliveryMode(2).build();
            channel.basicPublish(exchangeName, "", props, message.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 消息同步器装饰器
     */
    private class SyncWarpper {
        private volatile TransportEventEntry eventEntry;
        private CountDownLatch latch = new CountDownLatch(1);
        private long current = System.currentTimeMillis();

        public TransportEventEntry blockingResult() {
            try {
                boolean await = latch.await(config.getMaxBusBlockingTime(), TimeUnit.MILLISECONDS);
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
