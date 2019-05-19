package com.mcode.gateway.configuration;

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
     * mq事件队列容量
     */
    private int mqEventQueueSize;
    /**
     * 同步调用最大阻塞时间
     */
    private Integer maxBusBlockingTime;
    /**
     * 默认消息重发窗口时间
     */
    private Integer defaultTimeout;
}
