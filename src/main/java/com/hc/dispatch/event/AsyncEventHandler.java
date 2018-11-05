package com.hc.dispatch.event;


import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
/**
 * 扩展抽象类，标注为异步事件处理器
 */
@Slf4j
public abstract class AsyncEventHandler extends CommonUtil implements EventHandler {

}
