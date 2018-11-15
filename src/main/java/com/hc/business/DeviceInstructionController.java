package com.hc.business;

import com.hc.business.dto.DeliveryInstructionDTO;
import com.hc.business.service.DeviceInstructionService;
import com.hc.business.vo.BaseResult;
import com.hc.mvc.HttpMethod;
import com.hc.mvc.RestManager;
import com.hc.mvc.Route;
import com.hc.rpc.TransportEventEntry;
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
    public BaseResult publishInstruction(DeliveryInstructionDTO deliveryInstructionDTO) {
        validDTOEmpty(deliveryInstructionDTO);
        TransportEventEntry result = deviceInstructionService.publishInstruction(deliveryInstructionDTO);
        return new BaseResult(result);
    }
}
