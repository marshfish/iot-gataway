package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.configuration.ConfigCenter;
import com.hc.configuration.RedisConfig;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.message.MqConnector;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import com.hc.util.IdGenerator;
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
    private Gson gson;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private RedisConfig redisConfig;
    @Resource
    private ConfigCenter configCenter;
    @Override
    public void accept(TransportEventEntry event) {
        log.info("收到connector心跳");
        String connectorId = event.getConnectorId();
        Integer eqType = event.getEqType();
        validEmpty("实例ID", connectorId);
        validEmpty("设备类型", eqType);
        try (Jedis jedis = jedisPool.getResource()) {
            Long ttl = jedis.ttl(connectorId);
            if (ttl == -2) {
                log.error("该connector已过期，拒绝连接");
                reConnectPush(connectorId);
            } else if (ttl == -1) {
                log.error("connector不存在/connector已过期，拒绝连接");
                reConnectPush(connectorId);
            } else {
                jedis.expire(connectorId, redisConfig.getKeyExpire());
            }
        }
        TransportEventEntry eventEntry = new TransportEventEntry();
        eventEntry.setType(EventTypeEnum.PONG.getType());
        eventEntry.setSerialNumber(String.valueOf(IdGenerator.buildDistributedId()));
        eventEntry.setConnectorId(connectorId);
        eventEntry.setDispatcherId("1");
        //pong
        String eqName = configCenter.getEquipmentTypeRegistry().get(eqType);
        String downQueueName = mqConnector.getDownQueueName(eqName);
        mqConnector.publish(downQueueName,gson.toJson(eventEntry));
    }

    /**
     * 通知该节点断线重连
     * @param connectorId 旧节点ID
     */
    private void reConnectPush(String connectorId) {
        TransportEventEntry eventEntry = new TransportEventEntry();
        eventEntry.setType(EventTypeEnum.PONG.getType());
        eventEntry.setSerialNumber(String.valueOf(IdGenerator.buildDistributedId()));
        eventEntry.setConnectorId(connectorId);
    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.PING.getType();
    }
}
