package com.hc.business;

import com.hc.business.dal.dao.Configuration;
import com.hc.business.dto.ConfigDTO;
import com.hc.business.vo.BaseResult;
import com.hc.mvc.HttpMethod;
import com.hc.mvc.RestManager;
import com.hc.mvc.Route;
import com.hc.configuration.ConfigCenter;
import com.hc.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

import javax.annotation.Resource;
import java.util.List;

/**
 * 修改配置中心的配置
 */
@Slf4j
@Controller
@RestManager("/device/configuration")
public class DeviceConfigController extends CommonUtil {
    @Resource
    private ConfigCenter configCenter;

    @Route(value = "/equipment/value", method = HttpMethod.POST, desc = "动态添加设备类型配置")
    public BaseResult addEquipmentConfig(ConfigDTO configDTO) {
        validDTOEmpty(configDTO);
        configCenter.addEquipmentType(configDTO);
        return BaseResult.getInstance();
    }

    @Route(value = "/config/profile/value", method = HttpMethod.POST, desc = "动态添加环境类型")
    public BaseResult addProfileConfig(ConfigDTO configDTO) {
        validDTOEmpty(configDTO);
        configCenter.addProfile(configDTO);
        return BaseResult.getInstance();
    }

    @Route(value = "/config/protocol/value", method = HttpMethod.POST, desc = "动态添加协议类型")
    public BaseResult addProtocolConfig(ConfigDTO configDTO) {
        validDTOEmpty(configDTO);
        configCenter.addProtocol(configDTO);
        return BaseResult.getInstance();
    }

    @Route(value = "/equipment/value", method = HttpMethod.DELETE, desc = "动态删除设备类型配置")
    public BaseResult removeEquipmentConfig(ConfigDTO configDTO) {
        validEmpty("设备类型ID", configDTO.getType());
        configCenter.removeEquipmentType(configDTO.getType());
        return BaseResult.getInstance();
    }

    @Route(value = "/profile/value", method = HttpMethod.DELETE, desc = "动态删除环境类型")
    public BaseResult removeProfileConfig(ConfigDTO configDTO) {
        validEmpty("环境ID", configDTO.getType());
        configCenter.removeProfile(configDTO.getType());
        return BaseResult.getInstance();
    }

    @Route(value = "/protocol/value", method = HttpMethod.DELETE, desc = "动态删除协议类型")
    public BaseResult removeProtocolConfig(ConfigDTO configDTO) {
        validEmpty("协议ID", configDTO.getType());
        configCenter.removeProtocol(configDTO.getType());
        return BaseResult.getInstance();
    }

    @Route(value = "/all", method = HttpMethod.GET, desc = "获取全局配置信息")
    public BaseResult getEquipmentConfig() {
        List<Configuration> configurations = configCenter.getAllConfiguration();
        return new BaseResult(configurations);
    }
}
