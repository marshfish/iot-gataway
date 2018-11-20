package com.hc.rpc;

import com.hc.configuration.CommonConfig;
import com.hc.type.QosType;
import com.hc.util.SpringContextUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

@ToString
@Getter
public class PublishEvent {
    /**
     * 队列名
     */
    private String queue;
    /**
     * 消息体
     */
    private byte[] message;
    /**
     * 消息流水号
     */
    private String serialNumber;
    /**
     * mq消息头
     */
    private Map<String, Object> headers = new HashMap<>(3);
    /**
     * 消息重发窗口时间
     */
    @Setter
    private Integer timeout;
    /**
     * 服务质量
     */
    @Setter
    private Integer qos;
    /**
     * 时间戳
     */
    private long timeStamp;

    public PublishEvent(String queue, byte[] message, String serialNumber) {
        this.queue = queue;
        this.message = message;
        this.serialNumber = serialNumber;
        this.timeout = SpringContextUtil.getBean(CommonConfig.class).getDefaultTimeout();
        this.qos = QosType.AT_MOST_ONCE.getType();
        this.timeStamp = System.currentTimeMillis();
    }

    /**
     * 添加rabbitMq消息头
     */
    public void addHeaders(String key, Object value) {
        headers.put(key, value);
    }

}
