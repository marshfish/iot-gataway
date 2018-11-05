package com.hc.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;

@Slf4j
@Component
public class IdempotentUtil {
    @Resource
    private JedisPool jedisPool;

    private boolean isRepeatRequest(String messageId, long timeout) {
        try (Jedis jedis = jedisPool.getResource()) {
            return !"OK".equals(jedis.set(messageId, messageId, "NX", "PX", timeout));
        } catch (Exception e) {
            return false;
        }
    }

    private String createMessageUID(String message) {
        String id;
        try {
            id = DigestUtils.md5DigestAsHex(message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        return id;
    }

    public boolean doIdempotent(String message,long timeout) {
        if (StringUtils.isEmpty(message)) {
            return false;
        }
        String messageUID = createMessageUID(message);
        return isRepeatRequest(messageUID,timeout);
    }

}
