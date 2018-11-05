package com.hc.dispatch.event.handler;

import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConnectorResponseAsync extends AsyncEventHandler {
    @Override
    public void accept(TransportEventEntry event) {
        log.info("connector响应：{}",event);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.CONNECTOR_RESPONSE.getType();
    }
}
