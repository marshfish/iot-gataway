package com.mcode.gateway.dispatch.event.handler;

import com.mcode.gateway.dispatch.event.SyncEventHandler;
import com.mcode.gateway.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReceiveResponseSync extends SyncEventHandler {

    @Override
    public Integer setEventType() {
        return EventTypeEnum.CLIENT_RESPONSE.getType();
    }
}
