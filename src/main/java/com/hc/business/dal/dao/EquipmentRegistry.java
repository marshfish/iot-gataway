package com.hc.business.dal.dao;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Data
@Entity
public class EquipmentRegistry {
    @Id
    @GeneratedValue
    private Long id;
    /**
     * 设备系统唯一ID
     */
    private String uniqueId;
    /**
     * 设备ID
     */
    private String equipmentId;
    /**
     * 设备类型
     */
    private Integer equipmentType;
    /**
     * 设备通信协议
     */
    private Integer equipmentProtocol;
    /**
     * 设备所属环境
     */
    private Integer equipmentProfile;
    /**
     * 创建时间
     */
    private Long createTime;
    /**
     * 修改时间
     */
    private Long updateTime;
}
