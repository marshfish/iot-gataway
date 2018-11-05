package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.message.MqConnector;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import com.hc.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class Ping extends AsyncEventHandler {
    @Resource
    private MqConnector mqConnector;
    @Resource
    private Gson gson;

    @Override
    public void accept(TransportEventEntry event) {
        log.info("收到connector心跳");
        validEmpty("实例ID", event.getInstanceId());
        TransportEventEntry eventEntry = new TransportEventEntry();
        eventEntry.setType(EventTypeEnum.PONG.getType());
        eventEntry.setSerialNumber(String.valueOf(IdGenerator.buildDistributedId()));
        eventEntry.setInstanceId(event.getInstanceId());
        mqConnector.publish(gson.toJson(eventEntry), event.getInstanceId());
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.PING.getType();
    }
}
