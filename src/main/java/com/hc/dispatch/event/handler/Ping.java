package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.configuration.CommonConfig;
import com.hc.configuration.ConfigCenter;
import com.hc.configuration.RedisConfig;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.rpc.MqConnector;
import com.hc.rpc.TransportEventEntry;
import com.hc.rpc.serialization.Trans;
import com.hc.type.EventTypeEnum;
import com.hc.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class Ping extends AsyncEventHandler {
    @Resource
    private MqConnector mqConnector;
    @Resource
    private Gson gson;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private RedisConfig redisConfig;
    @Resource
    private ConfigCenter configCenter;
    @Resource
    private CommonConfig commonConfig;
    @Override
    public void accept(TransportEventEntry event) {
        log.info("收到connector心跳");
        String nodeArtifactId = event.getNodeArtifactId();
        Integer eqType = event.getEqType();
        String eqQueueName = event.getEqQueueName();
        validEmpty("实例ID", nodeArtifactId);
        validEmpty("设备类型", eqType);
        validEmpty("设备队列名", eqQueueName);
        try (Jedis jedis = jedisPool.getResource()) {
            Long ttl = jedis.ttl(nodeArtifactId);
            if (ttl == -2) {
                log.error("该connector已过期，拒绝连接");
                reConnectPush(eqQueueName, nodeArtifactId);
            } else if (ttl == -1) {
                log.error("connector不存在/connector已过期，拒绝连接");
                reConnectPush(eqQueueName, nodeArtifactId);
            } else {
                log.info("心跳成功，延长节点过期时间");
                jedis.expire(nodeArtifactId, redisConfig.getKeyExpire());
            }
        }
        Trans.event_data.Builder eventEntry = Trans.event_data.newBuilder();
        byte[] bytes = eventEntry.setType(EventTypeEnum.PONG.getType()).
                setSerialNumber(String.valueOf(IdGenerator.buildDistributedId())).
                setType(EventTypeEnum.PONG.getType()).
                setDispatcherId(commonConfig.getDispatcherId()).
                build().toByteArray();
        //pong
        Map<String, Object> headers = new HashMap<>();
        headers.put(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publish(eqQueueName, bytes, headers);
    }

    /**
     * 通知该节点断线重连
     */
    private void reConnectPush(String eqQueueName, String nodeArtifactId) {
        Trans.event_data.Builder eventEntry = Trans.event_data.newBuilder();
        byte[] bytes = eventEntry.setType(EventTypeEnum.DROPPED.getType()).
                setSerialNumber(String.valueOf(IdGenerator.buildDistributedId())).
                build().toByteArray();
        //pong
        Map<String, Object> headers = new HashMap<>();
        headers.put(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publish(eqQueueName, bytes, headers);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.PING.getType();
    }
}
