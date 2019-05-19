package com.mcode.gateway.rpc;

import lombok.Data;

/**
 * redis设备会话数据结构
 */
@Data
public class SessionEntry {
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
     * 设备所属节点ID
     */
    private String node;

}
