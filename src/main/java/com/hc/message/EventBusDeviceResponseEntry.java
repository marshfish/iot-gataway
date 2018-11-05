package com.hc.message;

import com.hc.mvc.NotNull;
import lombok.Data;

/**
 * eventBus设备响应结构
 */
@Data
public class EventBusDeviceResponseEntry {
    /**
     * 设备ID
     */
    @NotNull
    private String equipmentId;
    /**
     * 指令流水号
     */
    @NotNull
    private String serialNumber;
    /**
     * 指令
     */
    @NotNull
    private String instruction;
}
