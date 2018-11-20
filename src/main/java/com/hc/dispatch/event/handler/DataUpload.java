package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.Bootstrap;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.EquipmentRegisterDTO;
import com.hc.business.service.DeviceManagementService;
import com.hc.configuration.ConfigCenter;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.dispatch.event.MapDatabase;
import com.hc.rpc.MqConnector;
import com.hc.rpc.PublishEvent;
import com.hc.rpc.SessionEntry;
import com.hc.rpc.TransportEventEntry;
import com.hc.rpc.serialization.Trans;
import com.hc.type.EventTypeEnum;
import com.hc.util.AsyncHttpClient;
import com.rabbitmq.utility.Utility;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.omg.CORBA.PERSIST_STORE;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Slf4j
@Component
public class DataUpload extends AsyncEventHandler implements Bootstrap {
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private ConfigCenter configCenter;
    @Resource
    private DeviceManagementService deviceService;
    @Resource
    private MqConnector mqConnector;
    @Resource
    private MapDatabase mapDatabase;
    private static final Map<String, FailHandler> batchFail = Collections.synchronizedMap(new HashMap<>());
    private static final String RE_POST_MESSAGE = "http_backup";
    private static final ScheduledExecutorService rePostExecutor = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("rePost-exec-1");
        return thread;
    });

    @Override
    public void accept(TransportEventEntry event) {
        String eqId = event.getEqId();
        String uri = event.getUri();
        String msg = event.getMsg();
        Integer eqType = event.getEqType();
        String nodeArtifactId = event.getNodeArtifactId();
        String serialNumber = event.getSerialNumber();
        validEmpty("设备ID", eqId);
        validEmpty("上传uri", uri);
        validEmpty("上传消息体", msg);
        validEmpty("设备类型", eqType);
        validEmpty("设备节点ID", nodeArtifactId);
        validEmpty("流水号", serialNumber);
        log.info("接受设备上传的指令：{}", event);
        //验证connector是否注册,但仍然接受上传的数据
        String queue = mqConnector.getQueue(eqType);
        boolean register = validNodeRegister(nodeArtifactId);
        if (!register) {
            reConnectPush(queue, nodeArtifactId);
        }
        String uniqueId = MD5(eqType + eqId);
        try (Jedis jedis = jedisPool.getResource()) {
            String hget = jedis.hget(EquipmentLogin.SESSION_MAP, uniqueId);
            if (!StringUtils.isEmpty(hget)) {
                SessionEntry sessionEntry = gson.fromJson(hget, SessionEntry.class);
                Integer profile = sessionEntry.getProfile();
                String callbackDomain = configCenter.getProfileRegistry().get(profile);
                String url = callbackDomain + uri;
                AsyncHttpClient.sendPost(url, msg, new FailHandler(url, msg, serialNumber));
            } else {
                log.warn("设备会话不在线，但能正常通信，检查数据一致");
                EquipmentRegisterDTO equipmentRegisterDTO = new EquipmentRegisterDTO();
                equipmentRegisterDTO.setUniqueId(uniqueId);
                List<EquipmentRegistry> equipments = deviceService.selectEquipmentByCondition(equipmentRegisterDTO);
                EquipmentRegistry registry = equipments.get(0);
                if (registry != null) {
                    String callbackDomain = configCenter.getProfileRegistry().get(registry.getEquipmentProfile());
                    String url = callbackDomain + uri;
                    AsyncHttpClient.sendPost(url, msg, new FailHandler(url, msg, serialNumber));
                } else {
                    log.warn("该设备并未注册！，{}", event);
                }
            }
        }
        //确认上传成功
        byte[] bytes = Trans.event_data.newBuilder().
                setType(EventTypeEnum.UPLOAD_SUCCESS.getType()).
                setTimeStamp(System.currentTimeMillis()).
                setSerialNumber(serialNumber).build().toByteArray();
        PublishEvent publishEvent = new PublishEvent(mqConnector.getQueue(eqType), bytes, serialNumber);
        publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
        mqConnector.publishAsync(publishEvent);

    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_UPLOAD.getType();
    }

    @Override
    public void init() {
        rePostExecutor.scheduleAtFixedRate(() -> {
            try {
                //TODO 考虑限流和业务系统负载
                mapDatabase.read(FailHandler.class, RE_POST_MESSAGE).
                        forEach(handler -> {
                            String url = handler.getUrl();
                            String param = handler.getParam();
                            AsyncHttpClient.sendPost(url, param, new FailHandler(url, param, handler.getId()));
                        });
            } catch (Exception e) {
                log.error("重发线程异常：{}", Arrays.toString(e.getStackTrace()));
            }
        }, 180000, 10 * 60 * 1000, TimeUnit.MILLISECONDS);
    }

    @Getter
    private class FailHandler implements FutureCallback<HttpResponse> {
        private String url;
        private String param;
        private String id;

        public FailHandler(String url, String param, String serialNumber) {
            this.url = url;
            this.param = param;
            this.id = serialNumber;
        }

        @Override
        public void completed(HttpResponse httpResponse) {
            log.info("调用 {} 接口成功,参数：{}", url, param);
        }

        @Override
        public void failed(Exception e) {
            //TODO 批量插入DB，减少大量失败时频繁连接DB的cpu消耗，但可能漏数据，需根据业务动态调整
            if (batchFail.size() == 100) {
                batchFail.forEach((s, failHandler) -> mapDatabase.write(id, failHandler, RE_POST_MESSAGE));
                mapDatabase.write(id, this, RE_POST_MESSAGE).close();
                mapDatabase.close();
            } else {
                batchFail.put(id, this);
            }
        }

        @Override
        public void cancelled() {
            log.warn("取消调用{}接口,参数：{}", url, param);
        }
    }
}
