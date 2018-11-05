package com.hc.business.dto;

import com.hc.mvc.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 所有需要响应ack的HTTP请求接口，其参数都要继承该DTO
 */
@Slf4j
@Data
public class EquipmentDTO {
    /**
     * 指令流水号
     * 根据HTTP request自动生成
     * 相当于本条指令的主键
     */
    @NotNull
    private String serialNumber;
    /**
     * 设备系统唯一ID
     */
    @NotNull
    private String uniqueId;
    /**
     * 指令协议号
     */
    @NotNull
    private String protocolNumber;
    /**
     * 指令
     */
    @NotNull
    private String instruction;
}
