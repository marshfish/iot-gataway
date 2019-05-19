package com.mcode.gateway.business.service;

import com.mcode.gateway.business.dal.dao.EquipmentRegistry;
import com.mcode.gateway.business.dto.EquipmentRegisterDTO;

import java.util.List;

public interface DeviceManagementService {
    /**
     * 设备注册
     */
    String registeredDevice(EquipmentRegisterDTO equipmentDTO);

    /**
     * 删除设备
     */
    boolean deleteDevice(EquipmentRegisterDTO equipmentRegisterDTO);

    /**
     * 动态查询某一设备
     */
    List<EquipmentRegistry> selectEquipmentByCondition(EquipmentRegisterDTO equipmentRegisterDTO);

    /**
     * 动态修改设备信息
     */
    void updateEquipmentByCondition(EquipmentRegisterDTO equipmentRegisterDTO);
}
