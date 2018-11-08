package com.hc.dispatch;

import com.google.gson.Gson;
import com.hc.business.vo.BaseResult;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.EventHandlerPipeline;
import com.hc.dispatch.event.PipelineContainer;
import com.hc.dispatch.event.handler.ReceiveResponseAsync;
import com.hc.dispatch.event.handler.ReceiveResponseSync;
import com.hc.mvc.DispatcherProxy;
import com.hc.util.SpringContextUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP 下行请求处理
 */
@Slf4j
public class HttpDownStream extends AbstractVerticle {
    public static HttpServer httpServer;
    private CommonConfig commonConfig = SpringContextUtil.getBean(CommonConfig.class);
    private DispatcherProxy dispatcherProxy = SpringContextUtil.getBean(DispatcherProxy.class);
    private AtomicInteger instance = new AtomicInteger(1);

    @Resource

    @Override
    public void start() throws Exception {
        httpServer = vertx.createHttpServer(new HttpServerOptions().
                setIdleTimeout(commonConfig.getMaxHTTPIdleTime()));
        loadConnectionProcessor();
        loadBootstrapListener();
    }

    private void loadConnectionProcessor() {
        httpServer.requestHandler(request ->
                request.bodyHandler(buffer -> {
                    try {
                        //HTTP路由
                        String result = dispatcherProxy.routingHTTP(request, buffer.getString(0, buffer.length()));
                        writeSuccessResponse(request, result);
                    } catch (Exception e) {
                        log.error("HTTP request异常，{}", e);
                        writeFailResponse(request, e.getMessage());
                    }
                }).exceptionHandler(throwable -> {
                    //注意exceptionHandler里不能写响应，channel已经被关闭了，异常还是自己处理吧
                    log.error("HTTP request异常，{}", throwable);
                })).exceptionHandler(throwable -> log.error("HTTP服务器异常:{}", throwable));
    }

    private void loadBootstrapListener() {
        httpServer.listen(commonConfig.getHttpPort(), commonConfig.getHost(), httpServerAsyncResult -> {
            if (httpServerAsyncResult.succeeded()) {
                log.info("vert.x HTTP实例{}启动成功,端口：{}", instance.getAndIncrement(), commonConfig.getHttpPort());
            } else {
                log.info("vert.x HTTP实例{}启动成功,端口：{}", instance.getAndIncrement(), commonConfig.getHttpPort());
            }
        });
    }

    private void writeSuccessResponse(HttpServerRequest httpServerRequest, String result) {
        httpServerRequest.response().
                putHeader("content-eqType", "application/json").
                putHeader("Content-Length", String.valueOf(result.getBytes().length)).
                setStatusCode(200).
                write(result, "UTF-8").end();
    }

    private void writeFailResponse(HttpServerRequest httpServerRequest, String message) {
        String failMessage = new Gson().toJson(new BaseResult(500, message));
        httpServerRequest.response().
                setStatusCode(500).
                putHeader("content-eqType", "application/json").
                putHeader("Content-Length", String.valueOf(failMessage.getBytes().length))
                .write(failMessage, "UTF-8").end();
    }

}
