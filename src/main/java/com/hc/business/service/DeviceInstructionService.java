package com.hc.business.service;

import com.hc.business.dto.EquipmentDTO;

public interface DeviceInstructionService {
    /**
     * 发送指令
     */
    void publishInstruction(EquipmentDTO equipmentDTO);

}
