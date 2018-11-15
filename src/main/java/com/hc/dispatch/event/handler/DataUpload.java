package com.hc.dispatch.event.handler;

import com.google.gson.Gson;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.EquipmentRegisterDTO;
import com.hc.business.service.DeviceManagementService;
import com.hc.configuration.ConfigCenter;
import com.hc.dispatch.event.AsyncEventHandler;
import com.hc.rpc.SessionEntry;
import com.hc.rpc.TransportEventEntry;
import com.hc.type.EventTypeEnum;
import com.hc.util.AsyncHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class DataUpload extends AsyncEventHandler {
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private ConfigCenter configCenter;
    @Resource
    private DeviceManagementService deviceService;

    @Override
    public void accept(TransportEventEntry event) {
        String eqId = event.getEqId();
        String uri = event.getUri();
        String msg = event.getMsg();
        Integer eqType = event.getEqType();

        validEmpty("设备ID", eqId);
        validEmpty("上传uri", uri);
        validEmpty("上传消息体", msg);
        validEmpty("设备类型", eqType);
        log.info("接受设备上传的指令：{}",event);
        String uniqueId = MD5(eqType + eqId);
        try (Jedis jedis = jedisPool.getResource()) {
            String hget = jedis.hget(EquipmentLogin.SESSION_MAP, uniqueId);
            if (!StringUtils.isEmpty(hget)) {
                SessionEntry sessionEntry = gson.fromJson(hget, SessionEntry.class);
                Integer profile = sessionEntry.getProfile();
                String callbackDomain = configCenter.getProfileRegistry().get(profile);
                AsyncHttpClient.sendPost(callbackDomain + uri, msg);
            } else {
                log.warn("设备会话不在线，但能正常通信，检查数据一致");
                EquipmentRegisterDTO equipmentRegisterDTO = new EquipmentRegisterDTO();
                equipmentRegisterDTO.setUniqueId(uniqueId);
                List<EquipmentRegistry> equipments = deviceService.selectEquipmentByCondition(equipmentRegisterDTO);
                EquipmentRegistry registry = equipments.get(0);
                if (registry != null) {
                    String callbackDomain = configCenter.getProfileRegistry().get(registry.getEquipmentProfile());
                    AsyncHttpClient.sendPost(callbackDomain + uri, msg);
                } else {
                    log.warn("该设备并未注册！，{}",event);
                }
            }
        }

    }

    @Override
    public Integer setEventType() {
        return EventTypeEnum.DEVICE_UPLOAD.getType();
    }
}
