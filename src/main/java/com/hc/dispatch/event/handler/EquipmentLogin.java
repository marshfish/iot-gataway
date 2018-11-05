package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.exception.NullParamException;
import com.hc.message.MqConnector;
import com.hc.message.RedisEntry;
import com.hc.message.TransportEventEntry;
import com.hc.type.EquipmentTypeEnum;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class EquipmentLogin extends AsyncEventHandler {
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
    public void accept(TransportEventEntry event) {
        try {
            validDTOEmpty(event);
        } catch (NullParamException e) {
            log.warn("设备登陆事件异常：{}", e.getMessage());
            return;
        }
        String md5UniqueId = MD5(event.getEqType() + event.getEqId());
        List<EquipmentRegistry> equipment = equipmentDAL.getByUniqueId(md5UniqueId);
        if (CollectionUtils.isEmpty(equipment)) {
            //设备尚未注册
            log.warn("设备登陆失败，未注册，{}", event);
            event.setMsg("设备登陆失败，未注册");
            event.setType(EventTypeEnum.LOGIN_FAIL.getType());
            publishToConnector(event, event.getEqType());
        } else {
            //设备已注册
            EquipmentRegistry registry = equipment.get(0);
            try (Jedis jedis = jedisPool.getResource()) {
                RedisEntry redisEntry = new RedisEntry();
                redisEntry.setEqId(event.getEqId());
                redisEntry.setProfile(registry.getEquipmentProfile());
                redisEntry.setProtocol(registry.getEquipmentProtocol());
                redisEntry.setEqType(registry.getEquipmentType());
                Long hsetnx = jedis.hsetnx(CACHE_MAP, md5UniqueId, gson.toJson(redisEntry));
                if (hsetnx == 1) {
                    log.info("设备类型：【{}】，ID:【{}】从【{}】登陆", event.getEqType(),
                            event.getEqId(), event.getInstanceId());
                    //返回登陆成功事件，传入环境配置
                    event.setType(EventTypeEnum.LOGIN_SUCCESS.getType());
                    event.setProfile(registry.getEquipmentProfile());
                    publishToConnector(event, registry.getEquipmentType());
                } else {
                    log.warn("设备登陆失败，设备已登陆，{}", event);
                    event.setType(EventTypeEnum.LOGIN_FAIL.getType());
                    event.setMsg("设备登陆失败，设备已登陆");
                    publishToConnector(event, event.getEqType());
                }
            }
        }
    }

    /**
     * 推送给connector
     * @param entry 事件
     * @param eqType 设备类型
     */
    private void publishToConnector(TransportEventEntry entry, Integer eqType) {
        EquipmentTypeEnum equipmentTypeEnum = EquipmentTypeEnum.getEnumByCode(eqType);
        if (equipmentTypeEnum != null) {
            mqConnector.publish(equipmentTypeEnum.getQueueName(), gson.toJson(entry));
        } else {
            log.error("设备类型错误,event:{},eqType:{}", entry, eqType);
        }
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_LOGIN.getType();
    }
}
