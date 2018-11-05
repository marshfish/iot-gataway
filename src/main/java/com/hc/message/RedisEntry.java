package com.hc.message;

import lombok.Data;

/**
 * redis缓存数据结构
 */
@Data
public class RedisEntry {
    /**
     * 设备唯一ID
     */
    private String eqId;
    /**
     * 设备环境
     */
    private Integer profile;
    /**
     * 设备类型
     */
    private Integer eqType;
    /**
     * 设备协议
     */
    private Integer protocol;

}
