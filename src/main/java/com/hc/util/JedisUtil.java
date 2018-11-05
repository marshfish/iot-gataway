package com.hc.util;

import com.hc.configuration.RedisConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.Resource;


@Configuration
public class JedisUtil {
    @Resource
    private RedisConfig config;

    @Bean
    @Lazy
    JedisPoolConfig loadRedisConfig() {
        JedisPoolConfig jedisConfig = new JedisPoolConfig();
        jedisConfig.setMaxTotal(config.getMaxTotal());
        jedisConfig.setMaxIdle(config.getMaxIdle());
        jedisConfig.setMaxWaitMillis(config.getMaxWaitMills());
        jedisConfig.setTestOnBorrow(config.isTestOnBorrow());
        return jedisConfig;
    }

    @Bean
    @Lazy
    JedisPool loadRedisClient() {
        return new JedisPool(loadRedisConfig(),
                config.getAddress(),
                config.getPort(),
                config.getMaxWait(),
                config.getAuth(),
                config.getDatabaseIndex());
    }

}
