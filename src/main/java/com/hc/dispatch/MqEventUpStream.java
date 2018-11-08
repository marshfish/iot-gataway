package com.hc.dispatch;

import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.EventHandlerPipeline;
import com.hc.dispatch.event.PipelineContainer;
import com.hc.message.TransportEventEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 上行请求处理
 */
@Slf4j
@Component
@LoadOrder(value = 3)
public class MqEventUpStream implements Bootstrap {
    @Resource
    private CommonConfig commonConfig;
    @Resource
    private PipelineContainer pipelineContainer;
    private static Queue<TransportEventEntry> eventQueue;
    private static ExecutorService eventExecutor;

    private void initQueue() {
        eventQueue = new LinkedBlockingQueue<>(commonConfig.getEventBusQueueSize());
        eventExecutor = Executors.newFixedThreadPool(commonConfig.getEventBusThreadNumber(), new ThreadFactory() {
            private AtomicInteger count = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread();
                thread.setDaemon(true);
                thread.setName("event-poller-" + count.getAndIncrement());
                return thread;
            }
        });
    }

    public void handlerMessage(TransportEventEntry transportEventEntry) {
        if (!eventQueue.add(transportEventEntry)) {
            log.warn("HttpUpStream事件处理队列已满");
        }
    }


    private void exeEventLoop() {
        eventExecutor.execute(() -> {
            while (true) {
                TransportEventEntry event;
                if ((event = eventQueue.poll()) != null) {
                    try {
                        Integer eventType = event.getType();
                        String serialNumber = event.getSerialNumber();
                        Consumer<TransportEventEntry> consumer;
                        EventHandlerPipeline pipeline = pipelineContainer.getPipelineBySerialId(serialNumber);
                        //若未注册pipeline，使用默认的pipeline
                        if (pipeline == null) {
                            pipeline = pipelineContainer.getDefaultPipeline();
                        }
                        if ((consumer = pipeline.adaptEventHandler(eventType)) != null) {
                            consumer.accept(event);
                            pipelineContainer.removePipeline(serialNumber);
                        } else {
                            log.warn("未经注册的事件，{}", event);
                        }
                    } catch (Exception e) {
                        log.warn("事件处理异常，{}", e);
                    }
                }
            }
        });
    }
    @Override
    public void init() {
        log.info("load event queue and event poller thread");
        initQueue();
        exeEventLoop();
    }


}
