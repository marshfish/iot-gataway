package com.hc.dispatch.event;

import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.util.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@LoadOrder(value = 10)
public class PipelineContainer implements Bootstrap {
    //一个请求对应一个pipeline
    private static Map<String, EventHandlerPipeline> pipelineMap = new LRUHashMap<>();
    //默认公共的pipeline，所有实现了EventHandler的子类都会被注册到默认的pipeline中
    private static final EventHandlerPipeline defaultPipeline = new EventHandlerPipeline();

    //获取默认的pipeline
    public EventHandlerPipeline getDefaultPipeline() {
        return defaultPipeline;
    }

    //根据流水号获取pipeline
    public EventHandlerPipeline getPipelineBySerialId(String seriaId) {
        return pipelineMap.get(seriaId);
    }

    //添加pipeline
    public void addPipeline(String seriaId, EventHandlerPipeline pipeline) {
        pipelineMap.put(seriaId, pipeline);
    }

    //卸载pipeline
    public void removePipeline(String seriaId) {
        pipelineMap.remove(seriaId);
    }

    /**
     * 初始化defaultPipeline
     */
    @Override
    public void init() {
        log.info("load default event pipeline");
        SpringContextUtil.getContext().getBeansOfType(EventHandler.class).
                forEach((name, handler) -> defaultPipeline.addEventHandler(handler));
    }

    /**
     * 实现LRU的hashMap,预防内存泄漏
     */
    private static class LRUHashMap<K, V> extends LinkedHashMap<K, V> {

        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return true;
        }

        public LRUHashMap() {
            this(200, 0.75f);
        }

        public LRUHashMap(int initialCapacity, float loadFactor) {
            super(initialCapacity, loadFactor, true);
        }
    }
}
