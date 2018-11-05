package com.hc.mvc;

import com.google.gson.Gson;
import com.hc.business.dto.EquipmentDTO;
import com.hc.business.vo.BaseResult;
import com.hc.exception.MVCException;
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
public class DispatcherProxy {
    @Resource
    private IdempotentUtil idempotentUtil;
    @Resource
    private Gson gson;
    private static final Map<String, MappingEntry> HTTP_INSTRUCTION_MAPPING = new HashMap<>();
    private static final String PAGE_404 = new Gson().toJson(new BaseResult(404, "无法找到该Controller"));

    @SneakyThrows
    public void init() {
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

    public String routingHTTP(String requestId,HttpServerRequest request, String jsonBody) {
        return Optional.ofNullable(request.uri()).
                map(path -> HTTP_INSTRUCTION_MAPPING.get(path + request.method().name())).
                map(mappingEntry -> {
                    Method invokeMethod = mappingEntry.getMethod();
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
                    if (parameterTypes.length <= 0) {
                        return ReflectionUtil.invokeMethod(
                                mappingEntry.getObject(),
                                invokeMethod);
                    } else {
                        Object param = gson.fromJson(jsonBody.
                                replace("\n", "").
                                replace("\t", ""), parameterTypes[0]);
                        if (param == null) {
                            throw new MVCException("request body参数不能为空");
                        }
                        if (param instanceof EquipmentDTO) {
                            ((EquipmentDTO) param).setSerialNumber(requestId);
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

}
