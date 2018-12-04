package com.hc.dispatch;

import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.EventHandlerPipeline;
import com.hc.dispatch.event.PipelineContainer;
import com.hc.rpc.serialization.Trans;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private ArrayBlockingQueue<Trans.event_data> eventQueue;
    private ExecutorService eventExecutor;
    private final Object lock = new Object();

    private void initQueue() {
        eventQueue = new ArrayBlockingQueue<>(commonConfig.getMqEventQueueSize());
        eventExecutor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100), r -> {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    thread.setName("event-loop-1");
                    return thread;
                });
    }

    public void handlerMessage(Trans.event_data event) {
        synchronized (lock) {
            if (!eventQueue.add(event)) {
                log.warn("HttpUpStream事件处理队列已满");
            }
            lock.notify();
        }
    }

    /**
     * eventLoop单线程，纯内存操作目无须修改其线程数
     * 否则一定会出现线程安全问题，如果要执行阻塞操作参考{@link ClusterManager#getVertx()}
     */
    @SuppressWarnings({"Duplicates", "InfiniteLoopStatement"})
    private void exeEventLoop() {
        eventExecutor.execute(() -> {
            while (true) {
                Trans.event_data event;
                synchronized (lock) {
                    while ((event = eventQueue.poll()) == null) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                try {
                    Integer eventType = event.getType();
                    String serialNumber = event.getSerialNumber();
                    Consumer<Trans.event_data> consumer;
                    EventHandlerPipeline pipeline = pipelineContainer.getPipelineBySerialId(serialNumber);
                    //若未注册pipeline，使用默认的pipeline
                    if (pipeline == null) {
                        pipeline = pipelineContainer.getDefaultPipeline();
                    }
                    if ((consumer = pipeline.adaptEventHandler(eventType)) != null) {
                        consumer.accept(event);
                        pipelineContainer.removePipeline(serialNumber);
                    } else {
                        log.warn("未经注册的事件，{}", event.asString());
                    }
                } catch (Exception e) {
                    log.warn("事件处理异常，{}", e);
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
