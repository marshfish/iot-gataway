package com.hc.business.dto;

import com.hc.mvc.NotNull;
import lombok.Data;

/**
 * 配置中心DTO
 */
@Data
public class ConfigDTO {
    @NotNull
    private Integer type;
    @NotNull
    private String desc;
}
