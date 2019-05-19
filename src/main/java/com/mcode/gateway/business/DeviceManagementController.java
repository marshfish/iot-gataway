package com.mcode.gateway.business;

import com.mcode.gateway.business.dal.dao.EquipmentRegistry;
import com.mcode.gateway.business.dto.EquipmentRegisterDTO;
import com.mcode.gateway.business.service.DeviceManagementService;
import com.mcode.gateway.business.vo.BaseResult;
import com.mcode.gateway.mvc.HttpMethod;
import com.mcode.gateway.mvc.RestManager;
import com.mcode.gateway.mvc.Route;
import com.mcode.gateway.util.CommonUtil;
import com.mcode.gateway.util.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import redis.clients.jedis.JedisPool;

import javax.annotation.Resource;
import java.util.List;

/**
 * 设备管理api
 */
@Slf4j
@Controller
@RestManager("/device/management")
public class DeviceManagementController extends CommonUtil {
    @Resource
    private DeviceManagementService deviceManagementService;
    @Resource
    private JedisPool jedisPool;

    @Idempotent
    @Route(value = "/registry", method = HttpMethod.POST, desc = "注册设备")
    public BaseResult registryEquipment(EquipmentRegisterDTO equipmentRegisterDTO) {
        validDTOEmpty(equipmentRegisterDTO);
        String uniqueId = deviceManagementService.registeredDevice(equipmentRegisterDTO);
        return new BaseResult(uniqueId);
    }

    @Route(value = "/delete", method = HttpMethod.DELETE, desc = "删除设备")
    public BaseResult deleteEquipment(EquipmentRegisterDTO equipmentRegisterDTO) {
        validEmpty("设备系统唯一ID", equipmentRegisterDTO.getUniqueId());
        deviceManagementService.deleteDevice(equipmentRegisterDTO);
        return BaseResult.getInstance();
    }

    @Idempotent
    @Route(value = "/update/dynamic", method = HttpMethod.PUT, desc = "根据UniqueId动态修改设备")
    public BaseResult updateEquipmentRegistry(EquipmentRegisterDTO equipmentRegisterDTO) {
        validEmpty("设备系统唯一ID", equipmentRegisterDTO.getUniqueId());
        deviceManagementService.updateEquipmentByCondition(equipmentRegisterDTO);
        return BaseResult.getInstance();
    }

    @Route(value = "/select/dynamic", method = HttpMethod.POST, desc = "动态分页查询设备")
    public BaseResult selectEquipmentResult(EquipmentRegisterDTO equipmentRegisterDTO) {
        List<EquipmentRegistry> equipments = deviceManagementService.selectEquipmentByCondition(equipmentRegisterDTO);
        return new BaseResult(equipments);
    }

}
