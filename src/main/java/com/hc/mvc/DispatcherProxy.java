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
    private static final String RPC_MODEL = "rpc_model";
    private static final String RPC_TIMEOUT = "rpc_timeout";
    private static final String HEADER_SERIALIZE_ID = "serialId";
    private static final String QUALITY_OF_SERVICE = "qos";
    private static final String TIMEOUT = "qos_timeout";

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
                            String rpcModel = request.getHeader(RPC_MODEL);
                            String serialId = request.getHeader(HEADER_SERIALIZE_ID);
                            String responseQos = request.getHeader(QUALITY_OF_SERVICE);
                            String qosTimeout = request.getHeader(TIMEOUT);
                            String rpcTimeout = request.getHeader(RPC_TIMEOUT);
                            validEmpty("seriaId", serialId);
                            boolean rpc = Boolean.parseBoolean(rpcModel);
                            DeliveryInstructionDTO deliveryInstructionDTO = (DeliveryInstructionDTO) param;
                            //推送消息的流水号
                            deliveryInstructionDTO.setSerialNumber(serialId);
                            //是否RPC模式，是则挂起当前http请求，直到设备响应，返回给http response，或等待超时
                            // 注意：若开启RPC模式，则qos失效，重置为qos1，即最多发送一次
                            deliveryInstructionDTO.setRpcModel(rpc);
                            //若开启RPC的http请求挂起超时时间
                            deliveryInstructionDTO.setRpcTimeout(rpcTimeout == null ? commonConfig.getMaxHTTPIdleTime() :
                                    Integer.valueOf(rpcTimeout));
                            //是否自动重发消息，0仅发一次，1至少发一次，rpc默认重置为0
                            deliveryInstructionDTO.setQos((responseQos == null) || (rpc)
                                    ? QosType.AT_MOST_ONCE.getType() : Integer.valueOf(responseQos));
                            //消息重发窗口时间，配合qos，超过该时间则不再尝试重发
                            deliveryInstructionDTO.setQosTimeout(qosTimeout == null ? commonConfig.getDefaultTimeout() :
                                    Integer.valueOf(qosTimeout));
                            //根据是否开启RPC模式，动态添加同步/异步 响应事件处理器
                            ackDynamicPipeline(serialId, rpc);
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

    private void ackDynamicPipeline(String requestId, boolean isRpc) {
        PipelineContainer pipelineContainer = SpringContextUtil.getBean(PipelineContainer.class);
        EventHandlerPipeline defaultPipeline = pipelineContainer.getDefaultPipeline();
        EventHandlerPipeline thisPipeline = (EventHandlerPipeline) defaultPipeline.clone();
        if (isRpc) {
            //覆盖默认pipeline的同步/异步连接响应处理器
            thisPipeline.addEventHandler(SpringContextUtil.getBean(ReceiveResponseSync.class));
        } else {
            thisPipeline.addEventHandler(SpringContextUtil.getBean(ReceiveResponseAsync.class));
        }
        pipelineContainer.addPipeline(requestId, thisPipeline);
    }

}
