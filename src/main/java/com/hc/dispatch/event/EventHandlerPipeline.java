package com.hc.dispatch.event;

import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * eventHandler流水线
 * 动态添加/停用eventHandler
 */
@Slf4j
public class EventHandlerPipeline implements Cloneable{
    //pipeline中的消费者事件字典
    private Map<Integer, Consumer<TransportEventEntry>> eventHandler = new HashMap<>();

    public EventHandlerPipeline addEventHandler(EventHandler eventHandler) {
        Integer eventType = eventHandler.setEventType();
        addEventHandler(eventType, eventHandler);
        log.info("register event：{}", EventTypeEnum.getEnumByCode(eventType));
        return this;
    }

    /**
     * 添加事件处理器
     * @param eventType 事件类型
     * @param consumer 消费者
     */
    public void addEventHandler(Integer eventType, Consumer<TransportEventEntry> consumer) {
        eventHandler.put(eventType, consumer);
    }

    /**
     * 移除事件处理器
     * @param eventType 事件类型
     */
    public void removeEventHandler(Integer eventType) {
        eventHandler.remove(eventType);
    }

    /**
     * 根据事件类型获取事件处理器
     * @param eventType
     * @return
     */
    public Consumer<TransportEventEntry> adaptEventHandler(Integer eventType) {
        return eventHandler.get(eventType);
    }

    @Override
    public Object clone() {
        EventHandlerPipeline pipeline = null;
        try {
            pipeline = (EventHandlerPipeline) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return pipeline;
    }

}
