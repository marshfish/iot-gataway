package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.hc.configuration.CommonConfig;
import com.hc.configuration.ConfigCenter;
import com.hc.configuration.RedisConfig;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.rpc.MqConnector;
import com.hc.rpc.NodeEntry;
import com.hc.rpc.TransportEventEntry;
import com.hc.rpc.serialization.Trans;
import com.hc.type.ConfigTypeEnum;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

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
    @Resource
    private CommonConfig commonConfig;
    @Override
    public void accept(TransportEventEntry event) {
        String nodeArtifactId = event.getNodeArtifactId();
        String serialNumber = event.getSerialNumber();
        Integer eqType = event.getEqType();
        Integer protocol = event.getProtocol();
        String eqQueueName = event.getEqQueueName();
        validEmpty("节点项目唯一ID", nodeArtifactId);
        validEmpty("节点注册流水号", serialNumber);
        validEmpty("节点设备类型", eqType);
        validEmpty("节点设备协议", protocol);
        validEmpty("节点队列地址", eqQueueName);
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
        //注册节点，无需校验节点是否存在，心跳超时节点自动过期
        try (Jedis jeids = jedisPool.getResource()) {
            NodeEntry nodeEntry = new NodeEntry();
            nodeEntry.setEqType(eqType);
            nodeEntry.setNodeId(nodeArtifactId);
            nodeEntry.setNodeNumber(10000);
            nodeEntry.setProtocol(protocol);
            jeids.setex(nodeArtifactId, redisConfig.getKeyExpire(), gson.toJson(nodeEntry));
        }
        Map<Integer, String> map = configCenter.getConfigByConfigType(ConfigTypeEnum.ARTIFACT_PROFILE.getType());
//TODO dispatcherID
        Trans.event_data.Builder eventEntry = Trans.event_data.newBuilder();
        byte[] bytes = eventEntry.setType(EventTypeEnum.REGISTER_SUCCESS.getType()).
                setMsg(gson.toJson(map)).
                setNodeArtifactId(nodeArtifactId).
                setSerialNumber(serialNumber).
                setDispatcherId(commonConfig.getDispatcherId()).
                build().toByteArray();
        HashMap<String, Object> headers = new HashMap<>();
        headers.put(MqConnector.CONNECTOR_ID, nodeArtifactId);
        log.info("{}节点注册成功,设备：{}，协议:{}",nodeArtifactId,eqType,protocol);
        mqConnector.publish(eqQueueName, bytes, headers);

    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.INSTANCE_REGISTER.getType();
    }
}
