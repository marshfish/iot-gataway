package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.message.MqConnector;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;

@Slf4j
@Component
public class ConfigDiscover extends AsyncEventHandler {
    @Resource
    private EquipmentDAL equipmentDAL;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private MqConnector mqConnector;
    public static final String CACHE_MAP = "device_session";

    @Override
    public void accept(TransportEventEntry transportEventEntry) {
        //TODO 分布式配置中心
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.CONFIG_DISCOVER.getType();
    }
}
