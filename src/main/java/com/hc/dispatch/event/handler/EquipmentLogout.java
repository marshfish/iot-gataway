package com.hc.dispatch.event.handler;

import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.exception.NullParamException;
import com.hc.rpc.TransportEventEntry;
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
    private JedisPool jedisPool;

    @Override
    public void accept(TransportEventEntry event) {
        String eqId = event.getEqId();
        Integer eqType = event.getEqType();
        validEmpty("设备ID", eqId);
        validEmpty("设备类型", eqType);
        String md5UniqueId = MD5(eqType + eqId);
        log.info("设备{} 退出登录", md5UniqueId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel(EquipmentLogin.SESSION_MAP, md5UniqueId);
        }
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_LOGOUT.getType();
    }
}
