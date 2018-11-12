package com.hc.business.service.impl;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.EquipmentDTO;
import com.hc.business.service.DeviceInstructionService;
import com.hc.configuration.CommonConfig;
import com.hc.dispatch.event.handler.EquipmentLogin;
import com.hc.rpc.MqConnector;
import com.hc.rpc.SessionEntry;
import com.hc.rpc.TransportEventEntry;
import com.hc.rpc.serialization.Trans;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.List;


@Service
@Slf4j
public class DeviceInstructionServiceImpl extends CommonUtil implements DeviceInstructionService {
    /**
     * 查询db设备是否注册，获取设备类型
     * 查询redis，找到设备登陆的节点
     * 获取集群节点列表，向该节点发布消息
     */
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
    public TransportEventEntry publishInstruction(EquipmentDTO equipmentDTO) {
        String device;
        String md5UniqueId = equipmentDTO.getUniqueId();
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
            String node = sessionEntry.getNode();
            Trans.event_data.Builder entry = Trans.event_data.newBuilder();
            String serialNumber = equipmentDTO.getSerialNumber();
            String instruction = equipmentDTO.getInstruction();
            Boolean autoAck = equipmentDTO.getAutoAck();
            byte[] bytes = entry.setEqId(eqId).
                    setMsg(instruction).
                    setSerialNumber(serialNumber).
                    setDispatcherId(commonConfig.getDispatcherId()).
                    setNodeArtifactId(node).
                    setTimeStamp(System.currentTimeMillis()).
                    build().toByteArray();
            String routingKey = mqConnector.getRoutingKey(eqType);
            if (autoAck) {
                return mqConnector.publishSync(routingKey, serialNumber, bytes);
            } else {
                mqConnector.publish(routingKey, bytes);
                return null;
            }
        }
    }


}
