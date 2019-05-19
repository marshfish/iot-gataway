package com.mcode.gateway.business.service;

import com.mcode.gateway.business.dto.DeliveryInstructionDTO;
import com.mcode.gateway.business.service.impl.DeviceInstructionServiceImpl;

import java.lang.management.ThreadInfo;
import java.util.List;

public interface DeviceInstructionService {
    /**
     * 发送指令
     */
    String publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO);

    /**
     * 监控所有设备、所有节点的实时设备连接总数
     */
    List<DeviceInstructionServiceImpl.Response> monitor(Integer eqType);

    /**
     * dump某节点线程
     */
    String dump(String nodeName, Integer eqType);

    /**
     * dump本地线程
     */
    ThreadInfo[] dump();
}
