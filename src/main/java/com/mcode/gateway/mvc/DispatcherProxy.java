package com.mcode.gateway.mvc;

import com.google.gson.Gson;
import com.mcode.gateway.Bootstrap;
import com.mcode.gateway.LoadOrder;
import com.mcode.gateway.business.dto.DeliveryInstructionDTO;
import com.mcode.gateway.business.vo.BaseResult;
import com.mcode.gateway.configuration.CommonConfig;
import com.mcode.gateway.dispatch.event.EventHandlerPipeline;
import com.mcode.gateway.dispatch.event.PipelineContainer;
import com.mcode.gateway.dispatch.event.handler.ReceiveResponseSync;
import com.mcode.gateway.exception.MVCException;
import com.mcode.gateway.type.QosType;
import com.mcode.gateway.util.CommonUtil;
import com.mcode.gateway.util.Idempotent;
import com.mcode.gateway.util.IdempotentUtil;
import com.mcode.gateway.util.ReflectionUtil;
import com.mcode.gateway.util.SpringContextUtil;
import io.netty.util.internal.StringUtil;
import io.vertx.core.http.HttpServerRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final String FILTER_REPEAT_REQUEST = "请勿并发发送相同请求";
    private static final String FILTER_LIMIT_REQUEST = "该接口在%sms内仅允许访问一次";

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

    public String routingHTTP(HttpServerRequest request, String requestBody) {
        return Optional.ofNullable(request.uri()).
                map(path -> HTTP_INSTRUCTION_MAPPING.get(path.substring(0, path.indexOf("?")) +
                        request.method().name())).
                map(mappingEntry -> {
                    Method invokeMethod = mappingEntry.getMethod();
                    //幂等检查
                    Idempotent idempotent;
                    if ((idempotent = invokeMethod.getAnnotation(Idempotent.class)) != null) {
                        long timeout = idempotent.timeout();
                        Idempotent.Type model = idempotent.model();
                        switch (model) {
                            case REPEAT:
                                if (idempotentUtil.doIdempotent(request.uri() +
                                        request.method() +
                                        request.remoteAddress() +
                                        requestBody, timeout)) {
                                    return new BaseResult(FILTER_REPEAT_REQUEST);
                                }
                                break;
                            case LIMITING:
                                if (idempotentUtil.doIdempotent(request.uri(), timeout)) {
                                    return new BaseResult(String.format(FILTER_LIMIT_REQUEST, timeout));
                                }
                                break;
                            default:
                                return StringUtil.EMPTY_STRING;
                        }
                    }
                    //无参数
                    Class[] parameterTypes = invokeMethod.getParameterTypes();
                    if (parameterTypes.length == 0) {
                        return ReflectionUtil.invokeMethod(
                                mappingEntry.getObject(),
                                invokeMethod);
                    }
                    //GET
                    if (request.method().name().equals(HttpMethod.GET.getValue())) {
                        Object[] param = Stream.generate(() -> null).
                                limit(parameterTypes.length).
                                toArray();
                        String[] parameterNames = new LocalVariableTableParameterNameDiscoverer().getParameterNames(invokeMethod);
                        for (int i = 0; i < param.length; i++) {
                            String temp = request.getParam(parameterNames[i]);
                            if (StringUtils.isNotEmpty(temp)) {
                                param[i] = convert(parameterTypes[i], temp);
                            }
                        }
                        return ReflectionUtil.invokeMethod(
                                mappingEntry.getObject(),
                                invokeMethod, param);
                    }
                    //POST PUT DELETE
                    Object param = gson.fromJson(requestBody.
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
                        deliveryInstructionDTO.setRpcModel(rpc);
                        //若开启RPC的http请求挂起超时时间
                        deliveryInstructionDTO.setRpcTimeout(StringUtils.isEmpty(rpcTimeout) ?
                                commonConfig.getMaxHTTPIdleTime() : Integer.valueOf(rpcTimeout));
                        //是否自动重发消息，0仅发一次，1至少发一次，rpc默认重置为0
                        deliveryInstructionDTO.setQos(StringUtils.isEmpty(responseQos)
                                ? QosType.AT_MOST_ONCE.getType() : Integer.valueOf(responseQos));
                        //消息重发窗口时间，配合qos，超过该时间则不再尝试重发
                        deliveryInstructionDTO.setQosTimeout(StringUtils.isEmpty(qosTimeout) ?
                                commonConfig.getDefaultTimeout() : Integer.valueOf(qosTimeout));
                        //根据是否开启RPC模式，动态添加同步/异步 响应事件处理器
                        ackDynamicPipeline(serialId, rpc);
                    }
                    return ReflectionUtil.invokeMethod(
                            mappingEntry.getObject(),
                            invokeMethod,
                            param);
                }).
                map(baseResult -> gson.toJson(baseResult)).
                orElseGet(() -> {
                    log.warn("找不到该URI的处理器：{},参数：{}", request.uri() + request.method().name(), requestBody);
                    return PAGE_404;
                });
    }

    private Object convert(Class<?> parameterType, String temp) {
        String lowerCase = parameterType.getTypeName();
        if (lowerCase.equals(TypeName.INTEGER.getName()) || lowerCase.equals(TypeName.INT.getName())) {
            return Integer.valueOf(temp);
        } else if (lowerCase.equals(TypeName.LONGABLE.getName()) || lowerCase.equals(TypeName.LONG.getName())) {
            return Long.valueOf(temp);
        } else if (lowerCase.equals(TypeName.BOOLEANABLE.getName()) || lowerCase.equals(TypeName.BOOLEAN.getName())) {
            return Boolean.valueOf(temp);
        } else if (lowerCase.equals(TypeName.FLOATABLE.getName()) || lowerCase.equals(TypeName.FLOAT.getName())) {
            return Float.valueOf(temp);
        } else {
            return temp;
        }
    }

    public enum TypeName {
        INTEGER("java.lang.Integer"),
        STRING("java.lang.String"),
        LONGABLE("java.lang.Long"),
        BOOLEANABLE("java.lang.Boolean"),
        FLOATABLE("java.lang.Float"),

        INT("int"),
        LONG("long"),
        BOOLEAN("boolean"),
        FLOAT("float");

        private String name;

        TypeName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private void ackDynamicPipeline(String requestId, boolean isRpc) {
        if (isRpc) {
            PipelineContainer pipelineContainer = SpringContextUtil.getBean(PipelineContainer.class);
            EventHandlerPipeline defaultPipeline = pipelineContainer.getDefaultPipeline();
            EventHandlerPipeline thisPipeline = (EventHandlerPipeline) defaultPipeline.clone();
            //覆盖默认pipeline的异步连接响应处理器
            thisPipeline.addEventHandler(SpringContextUtil.getBean(ReceiveResponseSync.class));
            pipelineContainer.addPipeline(requestId, thisPipeline);
        }
    }

    public static void main(String[] args) {
        Object[] param = Stream.generate(() -> null).limit(20).collect(Collectors.toList()).toArray();
        System.out.println(param.length);
        int i = 0;
        for (Object obj : param) {

            if (obj == null) {
                param[0] = new Object();
            }
        }
    }
}
