package com.hc.dispatch;

import com.google.gson.Gson;
import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.configuration.CommonConfig;
import com.hc.rpc.serialization.Trans;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Optional;

@Slf4j
@Component
@LoadOrder(value = 1)
public class ClusterManager implements Bootstrap {
    @Resource
    private CommonConfig bean;
    @Resource
    private MqEventUpStream eventUpStream;
    @Resource
    private CommonConfig commonConfig;
    private static EventBus eventBus;
    private static Vertx vertx;

    @Override
    public void init() {
        log.info("load vertx cluster manager");
        Config hazelcastConfig = new Config().
                setGroupConfig(new GroupConfig().
                        setName(bean.getDispatcherId()).
                        setPassword(bean.getDispatcherId()));

        HazelcastClusterManager manager = new HazelcastClusterManager(hazelcastConfig);
        VertxOptions vertxOptions = new VertxOptions().
                setPreferNativeTransport(true).
                setClusterManager(manager);
        Vertx.clusteredVertx(vertxOptions, this::bootstrapHandler);
    }

    private void bootstrapHandler(AsyncResult<Vertx> event) {
        if (event.succeeded()) {
            vertx = event.result();
            vertx.deployVerticle(HttpDownStream.class, new DeploymentOptions().
                    setInstances(1));
            eventBus = vertx.eventBus();
            listen();
        } else {
            log.error("集群启动失败，{}", event.cause());
        }
    }

    /**
     * 监听本vertx节点的消息
     */
    public void listen() {
        log.info("load and listen eventBus ");
        eventBus.consumer(commonConfig.getDispatcherId(),
                (Handler<Message<byte[]>>) event -> {
                    try {
                        byte[] bytes = event.body();
                        eventUpStream.handlerMessage(Trans.event_data.parseFrom(bytes));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    /**
     * 发布消息到EventBus
     *
     * @param serialNumber 指令流水号
     * @param message      消息
     */
    public void publish(String serialNumber, Object message) {
        publish(serialNumber, message, null);
    }

    public void publish(String serialNumber, Object message, CaseInsensitiveHeaders headers) {
        publish(serialNumber, message, headers, 30 * 1000);
    }

    public void publish(String serialNumber, Object message, CaseInsensitiveHeaders headers, long sendTimeout) {
        Optional.ofNullable(ClusterManager.getEventBus()).ifPresent(eventBus ->
                eventBus.publish(serialNumber,
                        message,
                        new DeliveryOptions().setHeaders(headers).setSendTimeout(sendTimeout)));
    }

    /**
     * 获取集群Eventbus
     */
    public static EventBus getEventBus() {
        return eventBus;
    }

    /**
     * 获取vertx
     */
    public static Vertx getVertx() {
        return vertx;
    }

}
