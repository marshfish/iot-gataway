package com.hc.business;

import com.hc.business.dto.DeliveryInstructionDTO;
import com.hc.business.dto.MonitorDTO;
import com.hc.business.service.DeviceInstructionService;
import com.hc.business.service.impl.DeviceInstructionServiceImpl;
import com.hc.business.vo.BaseResult;
import com.hc.mvc.HttpMethod;
import com.hc.mvc.RestManager;
import com.hc.mvc.Route;
import com.hc.util.CommonUtil;
import com.hc.util.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import javax.annotation.Resource;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.Optional;

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
        String result = deviceInstructionService.publishInstruction(deliveryInstructionDTO);
        return new BaseResult(result);
    }

    @Idempotent(model = Idempotent.Type.LIMITING, timeout = 5000)
    @Route(value = "/monitor", method = HttpMethod.POST, desc = "监控设备连接列表,该接口5s内仅允许调用一次")
    public BaseResult monitor(MonitorDTO param) {
        synchronized (this) {
            String eqType = Optional.ofNullable(param).map(MonitorDTO::getEqType).orElse(null);
            List<DeviceInstructionServiceImpl.Response> monitor = deviceInstructionService.
                    monitor(eqType == null ? null : Integer.valueOf(eqType));
            return new BaseResult(monitor);
        }
    }

    @Route(value = "/dump/local", method = HttpMethod.GET, desc = "dump线程状态")
    public BaseResult dumpThis() {
        log.info("dump本地线程");
        ThreadInfo[] dump = deviceInstructionService.dump();
        return new BaseResult(dump);
    }

    @Route(value = "/dump/connector", method = HttpMethod.GET, desc = "dump线程状态")
    public BaseResult dumpConnector(String nodeName, Integer eqType) {
        validEmpty("节点名称", nodeName);
        validEmpty("设备类型", eqType);
        log.info("dump{}节点线程", nodeName);
        String dump = deviceInstructionService.dump(nodeName, eqType);
        return new BaseResult(dump);
    }

}
