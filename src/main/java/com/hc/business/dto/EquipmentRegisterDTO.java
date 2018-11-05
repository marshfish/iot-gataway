package com.hc.business.dto;

import com.hc.mvc.NotNull;
import lombok.Data;

@Data
public class EquipmentRegisterDTO extends PageDTO{
    @NotNull
    private String uniqueId;
    @NotNull
    private String equipmentId;
    @NotNull
    private Integer equipmentType;
    @NotNull
    private Integer equipmentProtocol;
    @NotNull
    private Integer equipmentProfile;
}
