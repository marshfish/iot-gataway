package com.hc.dispatch.event.handler;

import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.rpc.MqConnector;
import com.hc.rpc.PublishEvent;
import com.hc.rpc.serialization.Trans;
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
    @Resource
    private MqConnector mqConnector;

    @Override
    public void accept(Trans.event_data event) {
        String eqId = event.getEqId();
        Integer eqType = event.getEqType();
        String nodeArtifactId = event.getNodeArtifactId();
        String serialNumber = event.getSerialNumber();
        validEmpty("设备ID", eqId);
        validEmpty("设备类型", eqType);
        validEmpty("节点ID", nodeArtifactId);
        validEmpty("事件唯一ID",serialNumber);
        //验证connector是否注册
        String queue = mqConnector.getQueue(eqType);
        boolean register = validNodeRegister(nodeArtifactId);
        if (!register) {
            reConnectPush(queue, nodeArtifactId);
            return;
        }
        String md5UniqueId = MD5(eqType + eqId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel(EquipmentLogin.SESSION_MAP, md5UniqueId);
        }
        log.info("设备类型：【{}】，ID:【{}】从【{}】节点退出登陆,sessionId:【{}】",
                eqType, eqId, nodeArtifactId, md5UniqueId);
        Trans.event_data.Builder builder = Trans.event_data.newBuilder();
        byte[] bytes = builder.setType(EventTypeEnum.LOGOUT_SUCCESS.getType()).
                setEqId(eqId).
                setTimeStamp(System.currentTimeMillis()).
                setSerialNumber(serialNumber).
                build().toByteArray();
        PublishEvent publishEvent = new PublishEvent(queue, bytes, serialNumber);
        publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publishAsync(publishEvent);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_LOGOUT.getType();
    }
}
