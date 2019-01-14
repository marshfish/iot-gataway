package com.hc.dispatch;

import com.google.gson.Gson;
import com.hc.business.vo.BaseResult;
import com.hc.configuration.CommonConfig;
import com.hc.mvc.DispatcherProxy;
import com.hc.util.SpringContextUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import lombok.extern.slf4j.Slf4j;

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

    @Override
    public void start() throws Exception {
        //最大阻塞Http请求时间
        httpServer = vertx.createHttpServer(new HttpServerOptions().
                setPort(commonConfig.getHttpPort()).
                setIdleTimeout(5));
        loadConnectionProcessor();
        loadBootstrapListener();
    }

    private void loadConnectionProcessor() {
        Handler<HttpServerRequest> handler = request -> {
            Handler<Buffer> bodyHandler = buffer -> {
                try {
                    //HTTP路由
                    String result = dispatcherProxy.routingHTTP(request, buffer.getString(0, buffer.length()));
                    writeSuccessResponse(request, result);
                } catch (Exception e) {
                    log.error("HTTP request异常，{}", e);
                    writeFailResponse(request, e.getMessage());
                }
            };
            Handler<Throwable> exceptionHandler = throwable -> {
                //注意exceptionHandler里不能写响应，channel已经被关闭了，异常还是自己处理吧
                log.error("HTTP request异常，{}", throwable);
            };
            request.bodyHandler(bodyHandler).
                    exceptionHandler(exceptionHandler);
        };
        Handler<Throwable> exceptionHandler = throwable -> log.error("HTTP服务器异常:{}", throwable);
        httpServer.requestHandler(handler).
                exceptionHandler(exceptionHandler);
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
                putHeader("content-type", "application/json").
                putHeader("content-length", String.valueOf(result.getBytes().length)).
                setStatusCode(200).
                write(result, "UTF-8").end();
    }

    private void writeFailResponse(HttpServerRequest httpServerRequest, String message) {
        String failMessage = new Gson().toJson(new BaseResult(500, message));
        httpServerRequest.response().
                setStatusCode(500).
                putHeader("content-type", "application/json").
                putHeader("content-length", String.valueOf(failMessage.getBytes().length))
                .write(failMessage, "UTF-8").end();
    }

}
