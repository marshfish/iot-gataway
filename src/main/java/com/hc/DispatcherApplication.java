package com.hc;

import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hc.dispatch.HttpDownStream;
import com.hc.dispatch.MqEventUpStream;
import com.hc.mvc.DispatcherProxy;
import com.hc.util.SpringContextUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class DispatcherApplication {
    private static EventBus eventBus;
    private static HazelcastClusterManager manager;

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(DispatcherApplication.class);
        springApplication.setWebEnvironment(false);
        springApplication.run(args);

        com.hc.configuration.CommonConfig bean = SpringContextUtil.getBean(com.hc.configuration.CommonConfig.class);
        Config hazelcastConfig = new Config().
                setGroupConfig(new GroupConfig().
                        setName(bean.getArtifactId()).
                        setPassword(bean.getArtifactId()));

        manager = new HazelcastClusterManager(hazelcastConfig);
        VertxOptions vertxOptions = new VertxOptions().
                setPreferNativeTransport(true).
                setClusterManager(manager);
        Vertx.clusteredVertx(vertxOptions, DispatcherApplication::bootstrapHandler);
    }

    public static void bootstrapHandler(AsyncResult<Vertx> event) {
        if (event.succeeded()) {
            Vertx vertx = event.result();
            vertx.deployVerticle(HttpDownStream.class, new DeploymentOptions().
                    setInstances(1));
            eventBus = vertx.eventBus();
            SpringContextUtil.getBean(DispatcherProxy.class).init();
            SpringContextUtil.getBean(MqEventUpStream.class).init();
        } else {
            log.info("集群启动失败，{}", event.cause());
        }
    }

    public static EventBus getEventBus() {
        return eventBus;
    }
}
