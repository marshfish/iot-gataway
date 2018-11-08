package com.hc.message;

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
     * 节点编号
     */
    private Integer nodeNumber;
    /**
     * 设备类型
     */
    private Integer eqType;
    /**
     * 设备协议
     */
    private Integer protocol;
}
