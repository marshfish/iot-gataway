package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.hc.configuration.ConfigCenter;
import com.hc.configuration.RedisConfig;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.message.MqConnector;
import com.hc.message.NodeEntry;
import com.hc.message.TransportEventEntry;
import com.hc.type.ConfigTypeEnum;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class InstanceRegister extends AsyncEventHandler {
    private static final String INSTANCE_REGISTRY = "nodeRegistry";
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private MqConnector mqConnector;
    @Resource
    private ConfigCenter configCenter;
    @Resource
    private RedisConfig redisConfig;

    @Override
    public void accept(TransportEventEntry event) {
        String nodeArtifactId = event.getNodeArtifactId();
        String serialNumber = event.getSerialNumber();
        Integer eqType = event.getEqType();
        Integer protocol = event.getProtocol();
        validEmpty("节点项目唯一ID", nodeArtifactId);
        validEmpty("节点注册流水号", serialNumber);
        validEmpty("节点设备类型", eqType);
        validEmpty("节点设备协议", protocol);
        //校验是否存在该类型设备
        boolean equipmentName = configCenter.existEquipmentType(eqType);
        boolean protocolRegistry = configCenter.existProtocolType(protocol);
        if (!equipmentName) {
            log.warn("该类型设备未被注册，拒绝连接，{}", event);
            return;
        }
        if (!protocolRegistry) {
            log.warn("不支持该协议类型，拒绝连接，{}", event);
            return;
        }
        String uuid = UUID.randomUUID().toString();
        //注册节点，无需校验节点是否存在，心跳超时节点自动过期
        try (Jedis jeids = jedisPool.getResource()) {
            NodeEntry nodeEntry = new NodeEntry();
            nodeEntry.setEqType(eqType);
            nodeEntry.setNodeId(nodeArtifactId);
            nodeEntry.setNodeNumber(10000);
            nodeEntry.setProtocol(protocol);
            jeids.setex(nodeArtifactId, redisConfig.getKeyExpire(), gson.toJson(nodeEntry));
        }
        String eqName = configCenter.getEquipmentTypeRegistry().get(eqType);
        String downQueueName = mqConnector.getDownQueueName(eqName);
        Map<Integer, String> map = configCenter.getConfigByConfigType(ConfigTypeEnum.ARTIFACT_PROFILE.getType());
        TransportEventEntry eventEntry = new TransportEventEntry();
        eventEntry.setType(EventTypeEnum.REGISTER_SUCCESS.getType());
        eventEntry.setMsg(map);
        eventEntry.setConnectorId(nodeArtifactId);
        //TODO dispatcherID
        eventEntry.setDispatcherId("1");
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("connectorId", nodeArtifactId);
        mqConnector.publish(downQueueName, gson.toJson(eventEntry), headers);

    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.INSTANCE_REGISTER.getType();
    }

    public static void main(String[] args) {
        HashMap<Object, Object> map = new HashMap<>();
        map.put(123, "3wwer");
        map.put(666, "44444");
        TransportEventEntry eventEntry = new TransportEventEntry();
        eventEntry.setType(EventTypeEnum.REGISTER_SUCCESS.getType());
        eventEntry.setMsg(map);
        eventEntry.setConnectorId("1234567");
        String s = new Gson().toJson(eventEntry);
        TransportEventEntry eventEntry1 = new Gson().fromJson(s, TransportEventEntry.class);
        System.out.println(eventEntry1);
        LinkedTreeMap<Integer, String> msg = (LinkedTreeMap<Integer, String>) eventEntry1.getMsg();
        System.out.println(msg);
    }
}
