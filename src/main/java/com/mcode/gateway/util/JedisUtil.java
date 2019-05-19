package com.mcode.gateway.util;

import com.mcode.gateway.configuration.RedisConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.Resource;


@Configuration
public class JedisUtil {
    @Resource
    private RedisConfig config;

    @Bean
    JedisPoolConfig loadRedisConfig() {
        JedisPoolConfig jedisConfig = new JedisPoolConfig();
        jedisConfig.setMaxTotal(config.getMaxTotal());
        jedisConfig.setMaxIdle(config.getMaxIdle());
        jedisConfig.setMaxWaitMillis(config.getMaxWaitMills());
        jedisConfig.setTestOnBorrow(config.isTestOnBorrow());
        //连接线程等待的最大时间，若不配置，由于connector节点心跳间隔略长，
        // 可能导致连接池从池中拿到实际已断开的连接（JedisConnectionException）,导致connector节点频繁重连
        jedisConfig.setTestWhileIdle(true);
        //表示idle object evitor两次扫描之间要sleep的毫秒数
        jedisConfig.setTimeBetweenEvictionRunsMillis(config.getTimeBetweenEvictionRunsMillis());
        //表示idle object evitor每次扫描的最多的对象数
        jedisConfig.setNumTestsPerEvictionRun(config.getNumTestsPerEvictionRun());
        //表示一个对象至少停留在idle状态的最短时间，
        // 然后才能被idle object evitor扫描并驱逐；这一项只有在timeBetweenEvictionRunsMillis大于0时才有意义
        jedisConfig.setMinEvictableIdleTimeMillis(config.getMinEvictableIdleTimeMillis());
        return jedisConfig;
    }

    @Bean
    JedisPool loadRedisClient() {
        return new JedisPool(loadRedisConfig(),
                this.config.getAddress(),
                this.config.getPort(),
                this.config.getMaxWait(),
                this.config.getAuth(),
                this.config.getDatabaseIndex());
    }

}
