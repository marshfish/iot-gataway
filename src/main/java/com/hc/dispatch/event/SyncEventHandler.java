package com.hc.dispatch.event;


import com.hc.dispatch.CallbackManager;
import com.hc.rpc.TransportEventEntry;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * 同步事件处理器
 */
@Slf4j
public abstract class SyncEventHandler extends CommonUtil implements EventHandler {
    private CallbackManager callbackManager;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        callbackManager = beanFactory.getBean(CallbackManager.class);
    }

    @Override
    public void accept(TransportEventEntry event) {
        callbackManager.execCallback(event);
    }
}
