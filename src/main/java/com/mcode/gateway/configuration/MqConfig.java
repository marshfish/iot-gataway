package com.mcode.gateway.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * mq相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "dispatcher.rabbitmq")
public class MqConfig {
    /**
     * mq主机
     */
    private String mqHost;
    /**
     * 端口
     */
    private int mqPort;
    /**
     * 用户名
     */
    private String mqUserName;
    /**
     * 密码
     */
    private String mqPwd;
    /**
     * 虚拟机
     */
    private String virtualHost;
    /**
     * 上行队列名
     */
    private String upQueueName;
    /**
     * 交换机名
     */
    private String exchangeName;

}
