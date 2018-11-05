package com.hc.business.service;

import com.hc.business.dto.EquipmentDTO;
import com.hc.message.TransportEventEntry;

public interface DeviceInstructionService {
    /**
     * 发送指令
     */
    void publishInstruction(EquipmentDTO equipmentDTO);

}
