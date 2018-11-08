package com.hc.dispatch;

import com.hc.message.TransportEventEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 同步回调管理器
 */
@Slf4j
@Controller
public class CallbackManager {
    private static Map<String, Consumer<TransportEventEntry>> callbackInvoke = new ConcurrentHashMap<>(100);

    /**
     * 注册回调
     */
    public void registerCallbackEvent(String serialNumber, Consumer<TransportEventEntry> consumer) {
        callbackInvoke.put(serialNumber, consumer);
    }

    /**
     * 执行回调
     */
    public void execCallback(TransportEventEntry event) {
        Consumer<TransportEventEntry> consumer;
        String serialNumber = event.getSerialNumber();
        if ((consumer = callbackInvoke.get(serialNumber)) != null) {
            consumer.accept(event);
            callbackInvoke.remove(serialNumber);
        }
    }
}
