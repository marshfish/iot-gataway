package com.mcode.gateway.rpc;

import lombok.Data;

/**
 * redis  connector节点注册表结构
 */
@Data
public class NodeEntry {
    /**
     * 节点ID
     */
    private String nodeId;
    /**
     * 设备类型
     */
    private Integer eqType;
    /**
     * 设备协议
     */
    private Integer protocol;
}
