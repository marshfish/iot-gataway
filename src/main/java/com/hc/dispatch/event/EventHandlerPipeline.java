package com.hc.dispatch.event;

import com.hc.dispatch.event.handler.ConfigDiscover;
import com.hc.dispatch.event.handler.DataUpload;
import com.hc.dispatch.event.handler.EquipmentLogin;
import com.hc.dispatch.event.handler.EquipmentLogout;
import com.hc.dispatch.event.handler.InstanceRegister;
import com.hc.message.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import com.hc.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * eventHandler流水线
 * 动态添加/停用eventHandler
 */
@Slf4j
public class EventHandlerPipeline implements InitializingBean, Cloneable {
    //一个请求对应一个pipeline
    private static Map<String, EventHandlerPipeline> pipelineMap = new LRUHashMap<>();
    //pipeline中的消费者事件字典
    private Map<Integer, Consumer<TransportEventEntry>> eventHandler = new HashMap<>();
    //单例默认公共的pipeline
    private static final EventHandlerPipeline defaultPipeline = new EventHandlerPipeline();

    public static EventHandlerPipeline getPipelineBySerialId(String seriaId) {
        return pipelineMap.get(seriaId);
    }

    public static void addPipeline(String seriaId, EventHandlerPipeline pipeline) {
        pipelineMap.put(seriaId, pipeline);
    }

    public static EventHandlerPipeline getDefaultPipeline() {
        return defaultPipeline;
    }

    public EventHandlerPipeline addEventHandler(EventHandler eventHandler) {
        Integer eventType = eventHandler.setEventType();
        addEventHandler(eventType, eventHandler);
        log.info("注册事件：{}", EventTypeEnum.getEnumByCode(eventType));
        return this;
    }

    public void addEventHandler(Integer eventType, Consumer<TransportEventEntry> consumer) {
        eventHandler.put(eventType, consumer);
    }

    public void removeEventHandler(Integer eventType) {
        eventHandler.remove(eventType);
    }

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

    @Override
    public void afterPropertiesSet() throws Exception {
        defaultPipeline.addEventHandler(SpringContextUtil.getBean(ConfigDiscover.class))
                .addEventHandler(SpringContextUtil.getBean(DataUpload.class))
                .addEventHandler(SpringContextUtil.getBean(EquipmentLogin.class))
                .addEventHandler(SpringContextUtil.getBean(EquipmentLogout.class))
                .addEventHandler(SpringContextUtil.getBean(InstanceRegister.class));
    }
}
