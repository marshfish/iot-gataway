package com.hc.configuration;

import com.hc.business.dal.ConfigurationDAL;
import com.hc.business.dal.dao.Configuration;
import com.hc.business.dto.ConfigDTO;
import com.hc.type.ConfigTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置中心本地缓存
 * 环境、设备类型、设备协议
 */
@Component
@Slf4j
public class ConfigCenter implements InitializingBean {
    @Resource
    private ConfigurationDAL configurationDAL;

    private static Map<Integer, String> equipmentTypeRegistry = new ConcurrentHashMap<>(5);
    private static Map<Integer, String> profileRegistry = new ConcurrentHashMap<>(5);
    private static Map<Integer, String> protocolRegistry = new ConcurrentHashMap<>(5);
    private static final String EQUIPMENT_TYPE = "equipment_registry";
    private static final String PROFILE_TYPE = "profile_registry";
    private static final String PROTOCOL_TYPE = "protocol_registry";

    public void addEquipmentType(ConfigDTO configDTO) {
        if (equipmentTypeRegistry.get(configDTO.getType()) != null) {

        }
        equipmentTypeRegistry.putIfAbsent(configDTO.getType(), configDTO.getDesc());
        Configuration equipmentType = getConfiguration(configDTO, ConfigTypeEnum.EQUIPMENT_TYPE.getType());
        configurationDAL.save(equipmentType);
    }

    public void addProfile(ConfigDTO configDTO) {
        profileRegistry.putIfAbsent(configDTO.getType(), configDTO.getDesc());
        Configuration equipmentProfile = getConfiguration(configDTO, ConfigTypeEnum.ARTIFACT_PROFILE.getType());
        configurationDAL.save(equipmentProfile);
    }

    public void addProtocol(ConfigDTO configDTO) {
        protocolRegistry.putIfAbsent(configDTO.getType(), configDTO.getDesc());
        Configuration equipmentProtocol = getConfiguration(configDTO, ConfigTypeEnum.PROTOCOL.getType());
        configurationDAL.save(equipmentProtocol);
    }

    private Configuration getConfiguration(ConfigDTO configDTO, Integer configType) {
        Configuration configuration = new Configuration();
        configuration.setConfigType(configType);
        configuration.setValue(configDTO.getType());
        configuration.setDescKey(configDTO.getDesc());
        return configuration;
    }

    public void removeEquipmentType(int type) {
        protocolRegistry.remove(type);
        configurationDAL.removeByDescKeyAndConfigType(type, ConfigTypeEnum.EQUIPMENT_TYPE.getType());
    }

    public void removeProfile(int type) {
        profileRegistry.remove(type);
        configurationDAL.removeByDescKeyAndConfigType(type, ConfigTypeEnum.ARTIFACT_PROFILE.getType());
    }

    public void removeProtocol(int type) {
        protocolRegistry.remove(type);
        configurationDAL.removeByDescKeyAndConfigType(type, ConfigTypeEnum.PROTOCOL.getType());
    }

    public boolean existEquipmentType(int type) {
        if (equipmentTypeRegistry.containsKey(type)) {
            return true;
        } else {
            return existDB(type, EQUIPMENT_TYPE);
        }
    }

    public boolean existProfileType(int type) {
        if (profileRegistry.containsKey(type)) {
            return true;
        } else {
            return existDB(type, PROFILE_TYPE);
        }
    }

    public boolean existProtocolType(int type) {
        if (protocolRegistry.containsKey(type)) {
            return true;
        } else {
            return existDB(type, PROTOCOL_TYPE);
        }
    }

    private boolean existDB(int type, String configType) {
        List collection;
        switch (configType) {
            case EQUIPMENT_TYPE:
                collection = configurationDAL.getByDescKeyAndConfigType(type, ConfigTypeEnum.EQUIPMENT_TYPE.getType());
                break;
            case PROFILE_TYPE:
                collection = configurationDAL.getByDescKeyAndConfigType(type, ConfigTypeEnum.ARTIFACT_PROFILE.getType());
                break;
            case PROTOCOL_TYPE:
                collection = configurationDAL.getByDescKeyAndConfigType(type, ConfigTypeEnum.PROTOCOL.getType());
                break;
            default:
                return false;
        }
        if (!CollectionUtils.isEmpty(collection)) {
            reloadCache(configType);
            return true;
        }
        return false;
    }

    /**
     * 同步配置
     * 出于性能考虑没用全局锁，但concurrentHashMap无法保证原子性
     * 配置同步的概率很低，但也有可能有线程安全问题，故配置同步时直接丢弃掉原有的map，把引用指向newMap
     */
    private synchronized void reloadCache(String type) {
        Map<Integer, String> newMap = new ConcurrentHashMap<>(5);
        switch (type) {
            case EQUIPMENT_TYPE:
                configurationDAL.getByConfigType(ConfigTypeEnum.EQUIPMENT_TYPE.getType()).
                        forEach(configuration -> newMap.put(configuration.getValue(), configuration.getDescKey()));
                equipmentTypeRegistry = newMap;
                break;
            case PROFILE_TYPE:
                configurationDAL.getByConfigType(ConfigTypeEnum.ARTIFACT_PROFILE.getType()).
                        forEach(configuration -> newMap.put(configuration.getValue(), configuration.getDescKey()));
                profileRegistry = newMap;
                break;
            case PROTOCOL_TYPE:
                configurationDAL.getByConfigType(ConfigTypeEnum.PROTOCOL.getType()).
                        forEach(configuration -> newMap.put(configuration.getValue(), configuration.getDescKey()));
                protocolRegistry = newMap;
                break;
            default:
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        reloadCache(EQUIPMENT_TYPE);
        reloadCache(PROFILE_TYPE);
        reloadCache(PROTOCOL_TYPE);
    }

    public List<Configuration> getAllConfiguration() {
        List<Configuration> list = new ArrayList<>(15);
        configurationDAL.findAll().forEach(list::add);
        return list;
    }
}
