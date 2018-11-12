package com.hc.business.dal.dao;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Data
@Entity
public class Configuration {
    @Id
    @GeneratedValue
    private Long id;
    /**
     * 配置类型
     */
    private Integer configType;
    /**
     * 值
     */
    private Integer type;
    /**
     * 键
     */
    private String description;
    /**
     * 创建时间
     */
    private Long createTime;
    /**
     * 修改时间
     */
    private Long updateTime;
}
