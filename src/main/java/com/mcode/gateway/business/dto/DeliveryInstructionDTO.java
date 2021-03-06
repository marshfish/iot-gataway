package com.mcode.gateway.business.dto;

import com.mcode.gateway.mvc.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 所有需要响应ack的HTTP请求接口，其参数都要继承该DTO
 */
@Slf4j
@Data
public class DeliveryInstructionDTO {
    /**
     * 指令流水号
     * 由业务系统生成
     */
    @NotNull
    private String serialNumber;
    /**
     * 挂起请求并返回设备响应结果
     */
    @NotNull
    private Boolean rpcModel;
    /**
     * 挂起请求的最长时间
     */
    @NotNull
    private Integer rpcTimeout;
    /**
     * 设备系统唯一ID
     */
    @NotNull
    private String uniqueId;
    /**
     * 指令
     */
    @NotNull
    private String instruction;
    /**
     * 服务质量 0 至多发一次，1 最少发一次
     */
    @NotNull
    private Integer qos;
    /**
     * 最大超时时间
     */
    private Integer qosTimeout;

}
