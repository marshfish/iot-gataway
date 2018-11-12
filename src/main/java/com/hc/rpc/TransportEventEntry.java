package com.hc.rpc;

import com.hc.rpc.serialization.Trans;
import lombok.Data;

/**
 * dispatcher—connector 通用通信消息格式
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
     * connector节点项目名
     */
    private String nodeArtifactId;
    /**
     * 设备类型
     */
    private Integer eqType;
    /**
     * disptcher节点ID
     */
    private String dispatcherId;
    /**
     * 指令流水号
     */
    private String serialNumber;
    /**
     * 设备协议
     */
    private Integer protocol;
    /**
     * 指令
     */
    private String msg;
    /**
     * 环境配置
     */
    private Integer profile;
    /**
     * 设备队列地址
     */
    private String eqQueueName;
    /**
     * 发送时间戳
     */
    private Long timeStamp;

    /**
     * 数据上传uri
     */
    private String uri;

    public static TransportEventEntry parseTrans2This(Trans.event_data eventData) {
        TransportEventEntry eventEntry = new TransportEventEntry();
        eventEntry.setDispatcherId(eventData.getDispatcherId());
        eventEntry.setEqId(eventData.getEqId());
        eventEntry.setEqQueueName(eventData.getEqQueueName());
        eventEntry.setEqType(eventData.getEqType());
        eventEntry.setNodeArtifactId(eventData.getNodeArtifactId());
        eventEntry.setProfile(eventData.getProfile());
        eventEntry.setProtocol(eventData.getProtocol());
        eventEntry.setSerialNumber(eventData.getSerialNumber());
        eventEntry.setTimeStamp(eventData.getTimeStamp());
        eventEntry.setType(eventData.getType());
        eventEntry.setMsg(eventData.getMsg());
        eventEntry.setUri(eventData.getUri());
        return eventEntry;
    }
}
