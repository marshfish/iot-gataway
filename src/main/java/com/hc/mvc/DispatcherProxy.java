package com.hc.mvc;

import com.google.gson.Gson;
import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.business.dto.DeliveryInstructionDTO;
import com.hc.business.vo.BaseResult;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.EventHandlerPipeline;
import com.hc.dispatch.event.PipelineContainer;
import com.hc.dispatch.event.handler.ReceiveResponseAsync;
import com.hc.dispatch.event.handler.ReceiveResponseSync;
import com.hc.exception.MVCException;
import com.hc.type.QosType;
import com.hc.util.CommonUtil;
import com.hc.util.Idempotent;
import com.hc.util.IdempotentUtil;
import com.hc.util.ReflectionUtil;
import com.hc.util.SpringContextUtil;
import io.vertx.core.http.HttpServerRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@LoadOrder(value = 2)
public class DispatcherProxy extends CommonUtil implements Bootstrap {
    @Resource
    private IdempotentUtil idempotentUtil;
    @Resource
    private Gson gson;
    @Resource
    private CommonConfig commonConfig;
    private static final Map<String, MappingEntry> HTTP_INSTRUCTION_MAPPING = new HashMap<>();
    private static final String PAGE_404 = new Gson().toJson(new BaseResult(404, "无法找到该Controller"));
    private static final String HEADER_AUTO_ACK = "autoAck";
    private static final String HEADER_SERIALIZE_ID = "serialId";
    private static final String QUALITY_OF_SERVICE = "qos";
    private static final String TIMEOUT = "timeout";

    @SneakyThrows
    @Override
    public void init() {
        log.info("load http protocol mvc dispatcher");
        SpringContextUtil.getContext().getBeansWithAnnotation(RestManager.class).forEach((beanName, object) -> {
            Class<?> cls = object.getClass();
            Method[] methods = cls.getMethods();
            RestManager manager;
            if ((manager = cls.getAnnotation(RestManager.class)) != null) {
                for (Method method : methods) {
                    Route route;
                    if ((route = method.getAnnotation(Route.class)) != null) {
                        String uriKey = manager.value() + route.value() + route.method().getValue();
                        HTTP_INSTRUCTION_MAPPING.put(uriKey, new MappingEntry(object, method));
                    }
                }
            }
        });
    }

    public String routingHTTP(HttpServerRequest request, String jsonBody) {
        return Optional.ofNullable(request.uri()).
                map(path -> HTTP_INSTRUCTION_MAPPING.get(path + request.method().name())).
                map(mappingEntry -> {
                    Method invokeMethod = mappingEntry.getMethod();
                    //幂等检查
                    Idempotent idempotent;
                    if ((idempotent = invokeMethod.getAnnotation(Idempotent.class)) != null) {
                        long timeout = idempotent.timeout();
                        boolean repeat = idempotentUtil.doIdempotent(request.uri() +
                                request.method() +
                                request.remoteAddress() +
                                jsonBody, timeout);
                        if (repeat) {
                            return "请勿同时发送重复请求";
                        }
                    }
                    Class<?>[] parameterTypes = invokeMethod.getParameterTypes();
                    if (parameterTypes.length == 0) {
                        return ReflectionUtil.invokeMethod(
                                mappingEntry.getObject(),
                                invokeMethod);
                    } else {
                        Object param = gson.fromJson(jsonBody.
                                replace("\n", "").
                                replace("\t", ""), parameterTypes[0]);
                        if (param == null) {
                            throw new MVCException("request body不能为空");
                        }
                        //注入发送指令DTO
                        if (param instanceof DeliveryInstructionDTO) {
                            //解析指令推送参数
                            String responseAck = request.getHeader(HEADER_AUTO_ACK);
                            String serialId = request.getHeader(HEADER_SERIALIZE_ID);
                            String responseQos = request.getHeader(QUALITY_OF_SERVICE);
                            String maxTimeoutStr = request.getHeader(TIMEOUT);
                            validEmpty("seriaId", serialId);
                            boolean autoAck = Boolean.parseBoolean(responseAck);
                            //注入EquipmentDTO
                            DeliveryInstructionDTO deliveryInstructionDTO = (DeliveryInstructionDTO) param;
                            deliveryInstructionDTO.setSerialNumber(serialId);
                            deliveryInstructionDTO.setAutoAck(autoAck);
                            deliveryInstructionDTO.setQos(responseQos == null ? QosType.AT_MOST_ONCE.getType() :
                                    Integer.valueOf(responseQos));
                            deliveryInstructionDTO.setTimeout(maxTimeoutStr == null ? commonConfig.getDefaultTimeout() :
                                    Integer.valueOf(maxTimeoutStr));
                            //根据请求是否需要应答，添加同步/异步 响应事件处理器
                            AckDynamicPipeline(serialId, autoAck);
                        }
                        return ReflectionUtil.invokeMethod(
                                mappingEntry.getObject(),
                                invokeMethod,
                                param);
                    }
                }).
                map(baseResult -> gson.toJson(baseResult)).
                orElseGet(() -> {
                    log.warn("找不到该URI的处理器：{},参数：{}", request.uri() + request.method().name(), jsonBody);
                    return PAGE_404;
                });
    }

    private void AckDynamicPipeline(String requestId, boolean needAck) {
        PipelineContainer pipelineContainer = SpringContextUtil.getBean(PipelineContainer.class);
        EventHandlerPipeline defaultPipeline = pipelineContainer.getDefaultPipeline();
        EventHandlerPipeline thisPipeline = (EventHandlerPipeline) defaultPipeline.clone();
        if (needAck) {
            //覆盖默认pipeline的同步/异步连接响应处理器
            thisPipeline.addEventHandler(SpringContextUtil.getBean(ReceiveResponseSync.class));
        } else {
            thisPipeline.addEventHandler(SpringContextUtil.getBean(ReceiveResponseAsync.class));
        }
        pipelineContainer.addPipeline(requestId, thisPipeline);
    }

}
