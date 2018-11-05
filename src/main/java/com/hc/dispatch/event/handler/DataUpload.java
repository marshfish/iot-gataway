package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.exception.NullParamException;
import com.hc.message.MqConnector;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;

@Slf4j
@Component
public class DataUpload extends AsyncEventHandler {

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
            log.warn("设备数据上传事件异常：{}", e.getMessage());
            return;
        }
        //TODO 无状态数据上传
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_UPLOAD.getType();
    }
}
