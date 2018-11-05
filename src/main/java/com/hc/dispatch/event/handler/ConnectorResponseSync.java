package com.hc.dispatch.event.handler;

import com.hc.dispatch.event.SyncEventHandler;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConnectorResponseSync extends SyncEventHandler {

    @Override
    public Integer setEventType() {
        return EventTypeEnum.CONNECTOR_RESPONSE.getType();
    }
}
