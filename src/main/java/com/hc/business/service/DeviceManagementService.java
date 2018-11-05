package com.hc.business.service;

import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.EquipmentRegisterDTO;

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
