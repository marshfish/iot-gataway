package com.hc.dispatch.event;


import com.hc.message.MqConnector;
import com.hc.message.TransportEventEntry;

import java.util.function.Consumer;

/**
 * 事件处理器。connector与dispatcher的消息通信都被抽象成事件，根据业务不同分别提供了异步事件处理器（非阻塞）和同步事件处理器（阻塞），
 * 异步事件处理器：
 * 流程：
 *      发送消息：
 *      MqConnector[publish]方法推送给dispatcher端
 *      回调：
 *      RabbitMq发送消息 -> eventBus转发消息-> 相应节点的MqEventDownStream事件循环获取到事件，分配给EventHandler ->
 *      AsyncEventHandler调用handler方法
 * 同步事件处理器：继承同步事件处理器的子类不要重写accept方法，因为accept方法是异步的，重写后将无法实现同步调用
 * 流程：
 *      发送消息：
 *      MqConnector[publish]方法 -> Warpper.mockCallback设置同步回调-> rabbitMq推送 -> Warpper.blockingResult阻塞直到结果返回
 *      回调：
 *      RabbitMq发送消息 -> eventBus转发消息-> 相应节点的MqEventDownStream事件循环获取到事件，分配给EventHandler ->
 *      SyncEventHandler调用handler方法 -> CallbackManager获取同步回调的mockCallback -> 将响应结果set到Warpper里，并唤醒主线程 ->
 *      返回Warpper中的响应结果
 * rabbitmq发送消息详见{@link MqConnector#publishSync(String, String, String)}
 */
public interface EventHandler extends Consumer<TransportEventEntry>{
    /**
     * 事件处理
     * @param event 事件
     */
    void accept(TransportEventEntry event);
    /**
     * 设置事件类型
     * @return 事件类型
     */
    Integer setEventType();
}
