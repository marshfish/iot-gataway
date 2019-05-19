package com.mcode.gateway.dispatch.event.handler;

import com.mcode.gateway.business.service.DeviceInstructionService;
import com.mcode.gateway.business.service.impl.DeviceInstructionServiceImpl;
import com.mcode.gateway.dispatch.event.AsyncEventHandler;
import com.mcode.gateway.rpc.serialization.Trans;
import com.mcode.gateway.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class MonitorData extends AsyncEventHandler {
    @Resource
    private DeviceInstructionService instructionService;
    private Queue<Trans.event_data> nodesQueue = new LinkedBlockingQueue<>(10);
    private final Object monitor = new Object();

    @Override
    public void accept(Trans.event_data event) {
        synchronized (monitor) {
            nodesQueue.offer(event);
            monitor.notify();
        }
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.MONITOR_DATA.getType();
    }

    public MonitorWarpper warpperMonitor(int nodes, int timeout) {
        return new MonitorWarpper(nodes, timeout);
    }

    public class MonitorWarpper implements Callable<List<Trans.event_data>> {
        private List<Trans.event_data> nodes;
        private int timeout;

        public MonitorWarpper(int size, int timeout) {
            this.nodes = new ArrayList<>(size);
            this.timeout = timeout / size;
        }

        @Override
        public List<Trans.event_data> call() {
            int count = 1;
            while (count <= ((DeviceInstructionServiceImpl) instructionService).countNodeAndClear()) {
                synchronized (monitor) {
                    try {
                        //3s内未收到响应视为消息被丢弃,填充null
                        monitor.wait(timeout);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    count++;
                    Trans.event_data data;
                    if ((data = nodesQueue.poll()) != null) {
                        log.info("数据：{}", data);
                        nodes.add(data);
                    } else {
                        nodes.add(null);
                    }
                }
            }
            return nodes;
        }
    }
}
