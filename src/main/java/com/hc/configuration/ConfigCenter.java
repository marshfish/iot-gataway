package com.hc.configuration;

import com.hc.Bootstrap;
import com.hc.LoadOrder;
import com.hc.business.dal.ConfigurationDAL;
import com.hc.business.dal.dao.Configuration;
import com.hc.business.dto.ConfigDTO;
import com.hc.type.ConfigTypeEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 配置中心本地缓存
 * 环境、设备类型、设备协议
 */
@Component
@Slf4j
@LoadOrder(value = 4)
public class ConfigCenter implements Bootstrap {
    @Resource
    private ConfigurationDAL configurationDAL;
    @Getter
    private Map<Integer, String> equipmentTypeRegistry;
    @Getter
    private Map<Integer, String> profileRegistry;
    @Getter
    private Map<Integer, String> protocolRegistry;

    /**
     * 添加设备类型
     *
     * @param configDTO configDTO
     */
    @Transactional
    public void addEquipmentType(ConfigDTO configDTO) {
        Integer type = configDTO.getType();
        if (existEquipmentType(type)) {
            throw new RuntimeException("已存在该设备类型");
        }
        equipmentTypeRegistry.putIfAbsent(type, configDTO.getDescriptor());
        Configuration equipmentType = getConfiguration(configDTO, ConfigTypeEnum.EQUIPMENT_TYPE.getType());
        configurationDAL.save(equipmentType);
    }

    /**
     * 添加环境类型及回调地址
     *
     * @param configDTO configDTO
     */
    @Transactional
    public void addProfile(ConfigDTO configDTO) {
        Integer type = configDTO.getType();
        if (existProfileType(type)) {
            throw new RuntimeException("已存在该环境类型及回调地址");
        }
        profileRegistry.putIfAbsent(type, configDTO.getDescriptor());
        Configuration equipmentProfile = getConfiguration(configDTO, ConfigTypeEnum.ARTIFACT_PROFILE.getType());
        configurationDAL.save(equipmentProfile);
    }

    /**
     * 添加协议类型
     *
     * @param configDTO configDTO
     */
    @Transactional
    public void addProtocol(ConfigDTO configDTO) {
        Integer type = configDTO.getType();
        if (existProtocolType(type)) {
            throw new RuntimeException("已存在该协议类型");
        }
        protocolRegistry.putIfAbsent(configDTO.getType(), configDTO.getDescriptor());
        Configuration equipmentProtocol = getConfiguration(configDTO, ConfigTypeEnum.PROTOCOL.getType());
        configurationDAL.save(equipmentProtocol);
    }

    /**
     * newConfiguration
     *
     * @param configDTO  configDTO
     * @param configType 配置类型
     * @return Configuration
     */
    private Configuration getConfiguration(ConfigDTO configDTO, Integer configType) {
        Configuration configuration = new Configuration();
        configuration.setConfigType(configType);
        configuration.setType(configDTO.getType());
        configuration.setDescription(configDTO.getDescriptor());
        long now = System.currentTimeMillis();
        configuration.setCreateTime(now);
        configuration.setUpdateTime(now);
        return configuration;
    }

    /**
     * 删除设备类型
     *
     * @param type 设备类型
     */
    @Transactional
    public void removeEquipmentType(int type) {
        protocolRegistry.remove(type);
        configurationDAL.removeByTypeAndConfigType(type, ConfigTypeEnum.EQUIPMENT_TYPE.getType());
    }

    /**
     * 删除环境
     *
     * @param type 环境类型
     */
    @Transactional
    public void removeProfile(int type) {
        profileRegistry.remove(type);
        configurationDAL.removeByTypeAndConfigType(type, ConfigTypeEnum.ARTIFACT_PROFILE.getType());
    }

    /**
     * 删除协议
     *
     * @param type 协议类型
     */
    @Transactional
    public void removeProtocol(int type) {
        protocolRegistry.remove(type);
        configurationDAL.removeByTypeAndConfigType(type, ConfigTypeEnum.PROTOCOL.getType());
    }

    public boolean existEquipmentType(int type) {
        if (equipmentTypeRegistry.containsKey(type)) {
            return true;
        } else {
            return existDB(type, ConfigTypeEnum.EQUIPMENT_TYPE);
        }
    }

    public boolean existProfileType(int type) {
        if (profileRegistry.containsKey(type)) {
            return true;
        } else {
            return existDB(type, ConfigTypeEnum.ARTIFACT_PROFILE);
        }
    }

    public boolean existProtocolType(int type) {
        if (protocolRegistry.containsKey(type)) {
            return true;
        } else {
            return existDB(type, ConfigTypeEnum.PROTOCOL);
        }
    }

    /**
     * 查看DB中是否存在该配置，若存在，则说明配置被动态更新了，刷新缓存
     */
    private boolean existDB(int type, ConfigTypeEnum configType) {
        List collection;
        switch (configType) {
            case EQUIPMENT_TYPE:
                collection = configurationDAL.getByTypeAndConfigType(type, ConfigTypeEnum.EQUIPMENT_TYPE.getType());
                break;
            case ARTIFACT_PROFILE:
                collection = configurationDAL.getByTypeAndConfigType(type, ConfigTypeEnum.ARTIFACT_PROFILE.getType());
                break;
            case PROTOCOL:
                collection = configurationDAL.getByTypeAndConfigType(type, ConfigTypeEnum.PROTOCOL.getType());
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
    private synchronized void reloadCache(ConfigTypeEnum type) {
        Map<Integer, String> newMap = new ConcurrentHashMap<>(5);
        switch (type) {
            case EQUIPMENT_TYPE:
                configurationDAL.getByConfigType(ConfigTypeEnum.EQUIPMENT_TYPE.getType()).
                        forEach(configuration -> newMap.put(configuration.getType(), configuration.getDescription()));
                equipmentTypeRegistry = newMap;
                break;
            case ARTIFACT_PROFILE:
                configurationDAL.getByConfigType(ConfigTypeEnum.ARTIFACT_PROFILE.getType()).
                        forEach(configuration -> newMap.put(configuration.getType(), configuration.getDescription()));
                profileRegistry = newMap;
                break;
            case PROTOCOL:
                configurationDAL.getByConfigType(ConfigTypeEnum.PROTOCOL.getType()).
                        forEach(configuration -> newMap.put(configuration.getType(), configuration.getDescription()));
                protocolRegistry = newMap;
                break;
            default:
        }
    }

    /**
     * 获取所有配置信息
     *
     * @return List
     */
    public List<Configuration> getAllConfiguration() {
        List<Configuration> list = new ArrayList<>(15);
        configurationDAL.findAll().forEach(list::add);
        return list;
    }

    @Override
    public void init() {
        reloadCache(ConfigTypeEnum.EQUIPMENT_TYPE);
        reloadCache(ConfigTypeEnum.ARTIFACT_PROFILE);
        reloadCache(ConfigTypeEnum.PROTOCOL);
    }
}
