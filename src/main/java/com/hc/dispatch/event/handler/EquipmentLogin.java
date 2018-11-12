package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.configuration.ConfigCenter;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.rpc.SessionEntry;
import com.hc.rpc.MqConnector;
import com.hc.rpc.TransportEventEntry;
import com.hc.rpc.serialization.Trans;
import com.hc.type.EventTypeEnum;
import com.hc.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Resource
    private ConfigCenter configCenter;
    public static final String SESSION_MAP = "device_session";

    @Override
    public void accept(TransportEventEntry event) {
        Integer eqType = event.getEqType();
        String eqId = event.getEqId();
        String nodeArtifactId = event.getNodeArtifactId();
        String eqQueueName = event.getEqQueueName();
        validEmpty("节点ID", nodeArtifactId);
        validEmpty("设备类型", eqType);
        validEmpty("设备唯一ID", eqId);
        validEmpty("设备队列名", eqQueueName);
        //uniqueId规则，MD5(设备类型+设备ID)
        String md5UniqueId = MD5(eqType + eqId);
        List<EquipmentRegistry> equipment = equipmentDAL.getByUniqueId(md5UniqueId);
        //设备尚未注册
        if (CollectionUtils.isEmpty(equipment)) {
            log.warn("设备登陆失败，未注册，{}", event);
            Trans.event_data.Builder response = Trans.event_data.newBuilder();
            byte[] bytes = response.setMsg("设备登陆失败，未注册").
                    setType(EventTypeEnum.LOGIN_FAIL.getType()).
                    setNodeArtifactId(nodeArtifactId).
                    setEqId(eqId).
                    setSerialNumber(String.valueOf(IdGenerator.buildDistributedId())).
                    setTimeStamp(System.currentTimeMillis()).
                    build().toByteArray();
            publishToConnector(bytes, eqQueueName, nodeArtifactId);
        } else {
            //设备已注册
            Long hsetnx;
            EquipmentRegistry registry = equipment.get(0);
            try (Jedis jedis = jedisPool.getResource()) {
                SessionEntry eqSession = new SessionEntry();
                eqSession.setEqId(eqId);
                eqSession.setProfile(registry.getEquipmentProfile());
                eqSession.setEqType(registry.getEquipmentType());
                hsetnx = jedis.hsetnx(SESSION_MAP, md5UniqueId, gson.toJson(eqSession));
            }
            if (hsetnx == 1) {
                log.info("设备类型：【{}】，ID:【{}】从【{}】节点登陆", eqType, eqId, nodeArtifactId);
                //返回登陆成功事件，传入环境配置
                Trans.event_data.Builder response = Trans.event_data.newBuilder();
                byte[] bytes = response.setType(EventTypeEnum.LOGIN_SUCCESS.getType()).
                        setNodeArtifactId(nodeArtifactId).
                        setEqId(eqId).
                        setSerialNumber(String.valueOf(IdGenerator.buildDistributedId())).
                        setTimeStamp(System.currentTimeMillis()).
                        build().toByteArray();
                publishToConnector(bytes, eqQueueName, nodeArtifactId);
            } else {
                log.warn("设备登陆失败，设备已登陆，{}", event);
                Trans.event_data.Builder response = Trans.event_data.newBuilder();
                byte[] bytes = response.setMsg("设备登陆失败，设备已登陆").
                        setType(EventTypeEnum.LOGIN_FAIL.getType()).
                        setNodeArtifactId(nodeArtifactId).
                        setEqId(eqId).
                        setSerialNumber(String.valueOf(IdGenerator.buildDistributedId())).
                        setTimeStamp(System.currentTimeMillis()).
                        build().toByteArray();
                publishToConnector(bytes, eqQueueName, nodeArtifactId);
            }
        }
    }

    /**
     * 推送给connector
     *
     * @param bytes       事件
     * @param eqQueueName 设备队列名
     */
    private void publishToConnector(byte[] bytes, String eqQueueName, String nodeArtifactId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publish(eqQueueName, bytes);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_LOGIN.getType();
    }
}
