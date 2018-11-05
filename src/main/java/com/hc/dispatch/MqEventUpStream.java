package com.hc.dispatch;

import com.google.gson.Gson;
import com.hc.business.service.DeviceInstructionService;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.EventHandlerPipeline;
import com.hc.message.TransportEventEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 上行请求处理
 */
@Slf4j
@Component
public class MqEventUpStream {
    @Resource
    private CommonConfig commonConfig;
    private static Queue<TransportEventEntry> eventQueue;
    private static ExecutorService eventExecutor;

    private void initQueue() {
        eventQueue = new LinkedBlockingQueue<>(commonConfig.getEventBusQueueSize());
        eventExecutor = Executors.newFixedThreadPool(commonConfig.getEventBusThreadNumber());
    }

    public void handlerMessage(TransportEventEntry transportEventEntry) {
        if (!eventQueue.add(transportEventEntry)) {
            log.warn("HttpUpStream事件处理队列已满");
        }
    }


    private void exeEventLoop() {
        eventExecutor.submit((Runnable) () -> {
            while (true) {
                TransportEventEntry event = eventQueue.poll();
                if (event != null)
                    try {
                        //TODO 并发问题
                        Integer eventType = event.getType();
                        Consumer<TransportEventEntry> consumer;
                        EventHandlerPipeline pipeline = EventHandlerPipeline.getPipelineBySerialId(event.getSerialNumber());
                        if (pipeline == null) {
                            pipeline = EventHandlerPipeline.getDefaultPipeline();
                        }
                        if ((consumer = pipeline.adaptEventHandler(eventType)) != null) {
                            consumer.accept(event);
                        } else {
                            log.warn("未经注册的事件，{}", event);
                        }
                    } catch (Exception e) {
                        log.warn("事件处理异常，{}", e);
                    }
            }
        });
    }

    public void init() {
        initQueue();
        exeEventLoop();
    }


}
