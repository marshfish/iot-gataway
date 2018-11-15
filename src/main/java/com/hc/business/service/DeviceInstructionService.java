package com.hc.business.service;

import com.hc.business.dto.DeliveryInstructionDTO;
import com.hc.rpc.TransportEventEntry;

public interface DeviceInstructionService {
    /**
     * 发送指令
     */
    TransportEventEntry publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO);

}
