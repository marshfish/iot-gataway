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
import com.hc.type.QosType;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.List;

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
    public TransportEventEntry publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO) {
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
            SessionEntry sessionEntry = gson.fromJson(device, SessionEntry.class);
            String eqId = sessionEntry.getEqId();
            Integer eqType = sessionEntry.getEqType();
            String nodeArtifactId = sessionEntry.getNode();
            Trans.event_data.Builder entry = Trans.event_data.newBuilder();
            String serialNumber = deliveryInstructionDTO.getSerialNumber();
            String instruction = deliveryInstructionDTO.getInstruction();
            Boolean autoAck = deliveryInstructionDTO.getAutoAck();
            Integer qos = deliveryInstructionDTO.getQos();
            Integer timeout = deliveryInstructionDTO.getTimeout();

            byte[] bytes = entry.setEqId(eqId).
                    setType(EventTypeEnum.SERVER_PUBLISH.getType()).
                    setMsg(instruction).
                    setSerialNumber(serialNumber).
                    setDispatcherId(commonConfig.getDispatcherId()).
                    setTimeStamp(System.currentTimeMillis()).
                    setQos(qos).
                    setReTryTimeout(timeout).
                    build().toByteArray();
            String queue = mqConnector.getQueue(eqType);
            PublishEvent publishEvent = new PublishEvent(queue, bytes, serialNumber);
            publishEvent.setQos(qos);
            publishEvent.setTimeout(timeout);
            publishEvent.addHeaders(MqConnector.CONNECTOR_ID, nodeArtifactId);
            if (autoAck) {
                return mqConnector.publishSync(publishEvent);
            } else {
                mqConnector.publishAsync(publishEvent);
                return null;
            }
        }
    }


}
