package com.hc.business.service.impl;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.DeliveryInstructionDTO;
import com.hc.business.service.DeviceInstructionService;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.handler.EquipmentLogin;
import com.hc.rpc.MqConnector;
import com.hc.rpc.PublishEvent;
import com.hc.rpc.SessionEntry;
import com.hc.rpc.TransportEventEntry;
import com.hc.rpc.serialization.Trans;
import com.hc.type.EventTypeEnum;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * 查询db设备是否注册，获取设备类型
 * 查询redis，找到设备登陆的节点
 * 获取集群节点列表，向该节点发布消息
 */
@Service
@Slf4j
public class DeviceInstructionServiceImpl extends CommonUtil implements DeviceInstructionService {
    @Resource
    private EquipmentDAL equipmentDAL;
    @Resource
    private JedisPool jedisPool;
    @Resource
    private Gson gson;
    @Resource
    private MqConnector mqConnector;
    @Resource
    private CommonConfig commonConfig;

    @Override
    public String publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO) {
        String device;
        String md5UniqueId = deliveryInstructionDTO.getUniqueId();
        try (Jedis jedis = jedisPool.getResource()) {
            device = jedis.hget(EquipmentLogin.SESSION_MAP, md5UniqueId);
        }
        if (StringUtils.isEmpty(device)) {
            List<EquipmentRegistry> equipment = equipmentDAL.getByUniqueId(md5UniqueId);
            if (CollectionUtils.isEmpty(equipment)) {
                throw new RuntimeException("该设备不存在或未注册");
            } else {
                throw new RuntimeException("该设备不在线");
            }
        } else {
            //设备会话
            SessionEntry sessionEntry = gson.fromJson(device, SessionEntry.class);
            String eqId = sessionEntry.getEqId();
            Integer eqType = sessionEntry.getEqType();
            String nodeArtifactId = sessionEntry.getNode();
            //配置信息
            String serialNumber = deliveryInstructionDTO.getSerialNumber();
            String instruction = deliveryInstructionDTO.getInstruction();
            Boolean wait = deliveryInstructionDTO.getWait();
            Integer waitTimeout = deliveryInstructionDTO.getWaitTimeout();
            Integer qos = deliveryInstructionDTO.getQos();
            Integer qosTimeout = deliveryInstructionDTO.getQosTimeout();
            //序列化
            Trans.event_data.Builder entry = Trans.event_data.newBuilder();
            byte[] bytes = entry.setEqId(eqId).
                    setType(EventTypeEnum.SERVER_PUBLISH.getType()).
                    setMsg(instruction).
                    setSerialNumber(serialNumber).
                    setDispatcherId(commonConfig.getDispatcherId()).
                    setTimeStamp(System.currentTimeMillis()).
                    setQos(qos).
                    setReTryTimeout(qosTimeout).
                    build().toByteArray();
            String queue = mqConnector.getQueue(eqType);
            PublishEvent publishEvent = new PublishEvent(queue, bytes, serialNumber);
            publishEvent.setQos(qos);
            publishEvent.setTimeout(qosTimeout);
            publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
            //是否挂起请求
            if (wait) {
                TransportEventEntry eventEntry = mqConnector.publishSync(publishEvent, waitTimeout);
                return Optional.ofNullable(eventEntry).map(TransportEventEntry::getMsg).orElse(StringUtils.EMPTY);
            } else {
                mqConnector.publishAsync(publishEvent);
                return StringUtils.EMPTY;
            }
        }
    }


}
