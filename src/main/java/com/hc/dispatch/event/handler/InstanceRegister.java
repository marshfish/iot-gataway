package com.hc.dispatch.event.handler;

import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;

@Slf4j
@Component
public class InstanceRegister extends AsyncEventHandler {
    @Resource
    private JedisPool jedisPool;

    @Override
    public void accept(TransportEventEntry event) {
        try (Jedis jeids = jedisPool.getResource()) {

        }
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.INSTANCE_REGISTER.getType();
    }
}
