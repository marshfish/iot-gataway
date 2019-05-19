package com.mcode.gateway.dispatch.event.handler;

import com.mcode.gateway.configuration.CommonConfig;
import com.mcode.gateway.configuration.RedisConfig;
import com.mcode.gateway.dispatch.event.AsyncEventHandler;
import com.mcode.gateway.rpc.MqConnector;
import com.mcode.gateway.rpc.PublishEvent;
import com.mcode.gateway.rpc.serialization.Trans;
import com.mcode.gateway.type.EventTypeEnum;
import com.mcode.gateway.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;

@Slf4j
@Component
public class Ping extends AsyncEventHandler {
    @Resource
    private MqConnector mqConnector;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private RedisConfig redisConfig;
    @Resource
    private CommonConfig commonConfig;

    @Override
    public void accept(Trans.event_data event) {
        log.info("收到connector心跳");
        String nodeArtifactId = event.getNodeArtifactId();
        Integer eqType = event.getEqType();
        validEmpty("实例ID", nodeArtifactId);
        validEmpty("设备类型", eqType);
        String eqQueueName = mqConnector.getQueue(eqType);
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
        String id = String.valueOf(IdGenerator.buildDistributedId());
        Trans.event_data.Builder eventEntry = Trans.event_data.newBuilder();
        byte[] bytes = eventEntry.setType(EventTypeEnum.PONG.getType()).
                setSerialNumber(id).
                setType(EventTypeEnum.PONG.getType()).
                setDispatcherId(commonConfig.getDispatcherId()).
                build().toByteArray();
        //pong
        PublishEvent publishEvent = new PublishEvent(eqQueueName, bytes, id);
        publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publishAsync(publishEvent);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.PING.getType();
    }
}
