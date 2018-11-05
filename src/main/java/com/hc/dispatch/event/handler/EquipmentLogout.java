package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.dispatch.MqEventUpStream;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.exception.NullParamException;
import com.hc.message.MqConnector;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;

@Slf4j
@Component
public class EquipmentLogout extends AsyncEventHandler {
    @Resource
    private EquipmentDAL equipmentDAL;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private MqConnector mqConnector;
    public static final String CACHE_MAP = "device_session";
    @Resource
    private MqEventUpStream mqEventUpStream;

    @Override
    public void accept(TransportEventEntry event) {
        try {
            validDTOEmpty(event);
        } catch (NullParamException e) {
            log.warn("设备登出事件异常：{}", e.getMessage());
            return;
        }
        String md5UniqueId = MD5(event.getEqType() + event.getEqId());
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel(CACHE_MAP, md5UniqueId);
        }
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_LOGOUT.getType();
    }
}
