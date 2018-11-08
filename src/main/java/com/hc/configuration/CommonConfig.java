package com.hc.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "dispatcher.commons")
@Data
public class CommonConfig {
    /**
     * 项目ID
     */
    private String dispatcherId;
    /**
     * 最大HTTP阻塞时间
     */
    private int maxHTTPIdleTime;
    /**
     * HTTP端口
     */
    private int httpPort;
    /**
     * 域名
     */
    private String host;
    /**
     * 回调环境域名配置
     */
    private String devCallbackDomain;
    private String testCallbackDomain;
    private String preOnlineCallbackDomain;
    private String onlineCallbackDomain;
    /**
     * 集群通信事件处理线程数
     */
    private int eventBusThreadNumber;
    /**
     * 集群通信事件队列容量
     */
    private int eventBusQueueSize;
    private Integer instanceNumber;
    /**
     * 同步调用最大阻塞时间
     */
    private Integer maxBusBlockingTime;
}
