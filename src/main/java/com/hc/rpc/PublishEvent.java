package com.hc.rpc;

import com.hc.configuration.CommonConfig;
import com.hc.type.QosType;
import com.hc.util.SpringContextUtil;
import io.netty.util.HashedWheelTimer;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ToString(exclude = "message")
@Getter
public class PublishEvent {
    /**
     * 时间轮算法定时器
     */
    private transient static HashedWheelTimer timer = new HashedWheelTimer();
    /**
     * 设备系统唯一ID
     */
    private String uniqueId;
    /**
     * 重发次数
     */
    private transient int rePostCount = 0;
    /**
     * 是否入库
     */
    private transient boolean endurance = false;
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

    /**
     * 自增
     */
    public PublishEvent addRePostCount() {
        rePostCount++;
        return this;
    }

    /**
     * 设置入库flag
     */
    public void setEnduranceFlag(boolean flag) {
        this.endurance = flag;
    }

    /**
     * 添加timer，用于消息过期，重发校验
     */
    public void addTimer(Consumer<PublishEvent> consumer) {
        timer.newTimeout(timeout -> consumer.accept(this), 6000, TimeUnit.MILLISECONDS);
    }

    /**
     * 设置业务系统唯一ID
     */
    public void setUniqueId(String uniqueId){
        this.uniqueId=uniqueId;
    }
}
