package com.mcode.gateway.business.dto;

import com.mcode.gateway.mvc.NotNull;
import lombok.Data;

/**
 * 配置中心DTO
 */
@Data
public class ConfigDTO {
    @NotNull
    private Integer type;
    @NotNull
    private String descriptor;

}
