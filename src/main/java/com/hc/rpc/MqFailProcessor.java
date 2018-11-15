package com.hc.rpc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * rabbitMq死信处理
 */
@Slf4j
@Component
public class MqFailProcessor {
    private static ArrayBlockingQueue<PublishEvent> failQueue = new ArrayBlockingQueue<>(500);

    public void addFailMessage(PublishEvent failEvent) {
        if (!failQueue.offer(failEvent)) {
            log.warn("mq qos1重发队列已满，丢弃消息：{}", failEvent);
        }
    }

    public PublishEvent reDeliveryFailMessage() {
        long now = System.currentTimeMillis();
        PublishEvent event = failQueue.poll();
        if (event == null) {
            return null;
        }
        long lastTime = event.getTimeout() + event.getTimeStamp();
        return now > lastTime ? reDeliveryFailMessage() : event;
    }
}
