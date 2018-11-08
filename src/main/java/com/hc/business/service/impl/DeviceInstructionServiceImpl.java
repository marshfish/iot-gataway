package com.hc.business.service.impl;

import com.google.gson.Gson;
import com.hc.business.dal.EquipmentDAL;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.EquipmentDTO;
import com.hc.business.service.DeviceInstructionService;
import com.hc.configuration.ConfigCenter;
import com.hc.message.MqConnector;
import com.hc.message.RedisEntry;
import com.hc.message.TransportEventEntry;
import com.hc.type.EquipmentTypeEnum;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
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
    private ConfigCenter configCenter;
    public static final String CACHE_MAP = "device_session";

    @Override
    public void publishInstruction(EquipmentDTO equipmentDTO) {
        String device;
        String md5UniqueId = equipmentDTO.getUniqueId();
        try (Jedis jedis = jedisPool.getResource()) {
            device = jedis.hget(CACHE_MAP, md5UniqueId);
        }
        if (device == null) {
            List<EquipmentRegistry> equipment =
                    equipmentDAL.getByUniqueId(md5UniqueId);
            if (CollectionUtils.isEmpty(equipment)) {
                throw new RuntimeException("该设备不存在或未注册");
            } else {
                throw new RuntimeException("该设备不在线");
            }
        } else {
            RedisEntry redisEntry = gson.fromJson(device, RedisEntry.class);
            TransportEventEntry entry = new TransportEventEntry();
            entry.setEqId(redisEntry.getEqId());
            entry.setEqType(redisEntry.getEqType());
            entry.setMsg(equipmentDTO.getInstruction());
            entry.setSerialNumber(equipmentDTO.getSerialNumber());
            //TODO
            entry.setDispatcherId("1");
            //TODO
            entry.setConnectorId("");
            publishToConnector(entry, redisEntry.getEqType(),equipmentDTO.getSerialNumber());
        }

    }
    /**
     * 推送给connector
     * @param entry 事件
     * @param eqType 设备类型
     */
    private void publishToConnector(TransportEventEntry entry, Integer eqType,String seriaNumber) {
        EquipmentTypeEnum equipmentTypeEnum = EquipmentTypeEnum.getEnumByCode(eqType);
        if (equipmentTypeEnum != null) {
            String eqName = configCenter.getEquipmentTypeRegistry().get(eqType);
            String downQueueName = mqConnector.getDownQueueName(eqName);
            mqConnector.publishSync(downQueueName, gson.toJson(entry),
                    seriaNumber);
        } else {
            log.error("设备类型错误,event:{},eqType:{}", entry, eqType);
        }
    }


}
