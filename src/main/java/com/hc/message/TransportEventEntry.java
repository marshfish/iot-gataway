package com.hc.message;

import lombok.Data;

/**
 * dispatcher—connector 通信消息格式
 * 无用属性设为null，不序列化以减少IO成本
 */
@Data
public class TransportEventEntry {
    /**
     * 事件类型
     */
    private Integer type;
    /**
     * 设备唯一编号
     */
    private String eqId;
    /**
     * 设备类型
     */
    private Integer eqType;
    /**
     * 节点ID
     */
    private String instanceId;
    /**
     * 指令流水号
     */
    private String serialNumber;
    /**
     * 指令
     */
    private String msg;
    /**
     * 环境配置
     */
    private Integer profile;
}
