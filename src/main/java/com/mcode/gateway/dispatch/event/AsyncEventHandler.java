package com.mcode.gateway.dispatch.event;


import com.mcode.gateway.rpc.MqConnector;
import com.mcode.gateway.rpc.PublishEvent;
import com.mcode.gateway.rpc.serialization.Trans;
import com.mcode.gateway.type.EventTypeEnum;
import com.mcode.gateway.util.CommonUtil;
import com.mcode.gateway.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * 扩展抽象类，标注为异步事件处理器
 */
@Slf4j
public abstract class AsyncEventHandler extends CommonUtil implements EventHandler {
    private JedisPool jedisPool;
    private MqConnector mqConnector;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        jedisPool = beanFactory.getBean(JedisPool.class);
        mqConnector = beanFactory.getBean(MqConnector.class);
    }

    /**
     * 验证节点是否已经注册
     */
    protected boolean validNodeRegister(String nodeArtifactId) {
        try (Jedis jedis = jedisPool.getResource()) {
           return jedis.exists(nodeArtifactId);
        }
    }

    /**
     * 通知该节点断线重连
     */
    protected void reConnectPush(String eqQueueName, String nodeArtifactId) {
        String id = String.valueOf(IdGenerator.buildDistributedId());
        Trans.event_data.Builder eventEntry = Trans.event_data.newBuilder();
        byte[] bytes = eventEntry.setType(EventTypeEnum.DROPPED.getType()).
                setSerialNumber(id).
                setTimeStamp(System.currentTimeMillis()).
                build().toByteArray();
        //drop
        PublishEvent publishEvent = new PublishEvent(eqQueueName, bytes, id);
        publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publishAsync(publishEvent);
    }

}
