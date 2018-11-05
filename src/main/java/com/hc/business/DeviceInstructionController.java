package com.hc.business;

import com.hc.business.dto.EquipmentDTO;
import com.hc.business.service.DeviceInstructionService;
import com.hc.business.vo.BaseResult;
import com.hc.mvc.HttpMethod;
import com.hc.mvc.RestManager;
import com.hc.mvc.Route;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import javax.annotation.Resource;

/**
 * 设备指令发送api
 */
@Slf4j
@Controller
@RestManager("/device/instruction")
public class DeviceInstructionController extends CommonUtil {
    @Resource
    private DeviceInstructionService deviceInstructionService;

    @Route(value = "/publish", method = HttpMethod.POST, desc = "推送设备指令")
    public BaseResult publishInstruction(EquipmentDTO equipmentDTO) {
        validDTOEmpty(equipmentDTO);
        deviceInstructionService.publishInstruction(equipmentDTO);
        return BaseResult.getInstance();
    }

    @Route(value = "/registry/valid", method = HttpMethod.POST, desc = "校验设备是否注册")
    public BaseResult validHasRegistered() {
        return BaseResult.getInstance();
    }
}
