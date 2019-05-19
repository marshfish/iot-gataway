package com.mcode.gateway.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * redis相关配置
 */
@Component
@ConfigurationProperties(prefix = "dispatcher.redis")
@Data
public class RedisConfig {
    private int maxTotal;
    private int maxIdle;
    private int maxWaitMills;
    private boolean testOnBorrow;
    private String address;
    private int port;
    private int maxWait;
    private String auth;
    private int databaseIndex;
    private int keyExpire;
    private int timeBetweenEvictionRunsMillis;
    private int numTestsPerEvictionRun;
    private int minEvictableIdleTimeMillis;
}
