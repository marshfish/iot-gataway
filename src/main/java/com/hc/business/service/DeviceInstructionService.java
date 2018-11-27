package com.hc.business.service;

import com.hc.business.dto.DeliveryInstructionDTO;

public interface DeviceInstructionService {
    /**
     * 发送指令
     */
    String publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO);

}
