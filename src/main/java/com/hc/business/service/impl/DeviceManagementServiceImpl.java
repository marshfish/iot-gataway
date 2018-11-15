package com.hc.business.service.impl;

import com.hc.business.dal.EquipmentDAL;
import com.hc.business.dal.dao.EquipmentRegistry;
import com.hc.business.dto.EquipmentRegisterDTO;
import com.hc.business.service.DeviceManagementService;
import com.hc.configuration.ConfigCenter;
import com.hc.util.CommonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

@Service
public class DeviceManagementServiceImpl extends CommonUtil implements DeviceManagementService {
    @Resource
    private EquipmentDAL equipmentDAL;
    @Resource
    private ConfigCenter configCenter;

    @Override
    @Transactional
    public String registeredDevice(EquipmentRegisterDTO equipmentDTO) {
        Integer equipmentType = equipmentDTO.getEquipmentType();
        Integer profile = equipmentDTO.getEquipmentProfile();
        Integer protocol = equipmentDTO.getEquipmentProtocol();
        String equipmentId = equipmentDTO.getEquipmentId();
        //根据配置中心校验
        if (!configCenter.existEquipmentType(equipmentType)) {
            throw new RuntimeException("该设备类型不存在，需要配置设备类型");
        }
        if (!configCenter.existProfileType(profile)) {
            throw new RuntimeException("该环境不存在，需要配置项目环境");
        }
        if (!configCenter.existProtocolType(profile)) {
            throw new RuntimeException("该协议不存在，需要配置协议支持");
        }
        //校验是否已注册
        String md5UniqueId = MD5(equipmentType + equipmentId);
        List<EquipmentRegistry> exist = equipmentDAL.getByUniqueId(md5UniqueId);
        if (!CollectionUtils.isEmpty(exist)) {
            throw new RuntimeException("该设备已被注册");
        }
        //注册
        EquipmentRegistry equipmentRegistry = new EquipmentRegistry();
        equipmentRegistry.setUniqueId(md5UniqueId);
        equipmentRegistry.setEquipmentId(equipmentId);
        equipmentRegistry.setEquipmentProfile(profile);
        equipmentRegistry.setEquipmentProtocol(protocol);
        equipmentRegistry.setEquipmentType(equipmentType);
        long now = System.currentTimeMillis();
        equipmentRegistry.setCreateTime(now);
        equipmentRegistry.setUpdateTime(now);
        equipmentDAL.save(equipmentRegistry);
        return md5UniqueId;
    }

    @Override
    @Transactional
    public boolean deleteDevice(EquipmentRegisterDTO equipmentRegisterDTO) {
        return equipmentDAL.deleteByUniqueId(equipmentRegisterDTO.getUniqueId()) > 0;
    }

    @Override
    @Transactional
    public List<EquipmentRegistry> selectEquipmentByCondition(EquipmentRegisterDTO equipmentRegisterDTO) {
        String uniqueId = equipmentRegisterDTO.getUniqueId();
        String equipmentId = equipmentRegisterDTO.getEquipmentId();
        Integer equipmentProfile = equipmentRegisterDTO.getEquipmentProfile();
        Integer equipmentProtocol = equipmentRegisterDTO.getEquipmentProtocol();
        Integer equipmentType = equipmentRegisterDTO.getEquipmentType();
        //分页
        Pageable pageable = new PageRequest(equipmentRegisterDTO.getPageNumber(), equipmentRegisterDTO.getPageSize());
        //动态查询
        Specification<EquipmentRegistry> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(6);
            if (StringUtils.isNotBlank(uniqueId)) {
                predicates.add(cb.equal(root.get("eqId"), uniqueId));
            }
            if (StringUtils.isNotBlank(equipmentId)) {
                predicates.add(cb.equal(root.get("equipmentId"), equipmentId));
            }
            if (equipmentProfile != null) {
                predicates.add(cb.equal(root.get("equipmentProfile"), equipmentProfile));
            }
            if (equipmentProtocol != null) {
                predicates.add(cb.equal(root.get("equipmentProtocol"), equipmentProtocol));
            }
            if (equipmentType != null) {
                predicates.add(cb.equal(root.get("equipmentType"), equipmentType));
            }
            return cb.and(predicates.toArray(new Predicate[predicates.size()]));
        };
        Page<EquipmentRegistry> all = equipmentDAL.findAll(specification, pageable);
        Iterator<EquipmentRegistry> iterator = all.iterator();
        List<EquipmentRegistry> registries = new ArrayList<>();
        while (iterator.hasNext()) {
            EquipmentRegistry next = iterator.next();
            registries.add(next);
        }
        registries.sort(Comparator.comparingInt(o -> o.getCreateTime().intValue()));
        return registries;
    }

    @Override
    @Transactional
    public void updateEquipmentByCondition(EquipmentRegisterDTO equipmentRegisterDTO) {
        String uniqueId = equipmentRegisterDTO.getUniqueId();
        String equipmentId = equipmentRegisterDTO.getEquipmentId();
        Integer equipmentProfile = equipmentRegisterDTO.getEquipmentProfile();
        Integer equipmentProtocol = equipmentRegisterDTO.getEquipmentProtocol();
        Integer equipmentType = equipmentRegisterDTO.getEquipmentType();

        List<EquipmentRegistry> byUniqueId = equipmentDAL.getByUniqueId(uniqueId);
        if (!CollectionUtils.isEmpty(byUniqueId)) {
            EquipmentRegistry equipmentRegistry = byUniqueId.get(0);
            if (StringUtils.isNotBlank(equipmentId)) {
                equipmentRegistry.setEquipmentId(equipmentId);
            }
            if (equipmentProfile != null) {
                equipmentRegistry.setEquipmentProfile(equipmentProfile);
            }
            if (equipmentProtocol != null) {
                equipmentRegistry.setEquipmentProtocol(equipmentProtocol);
            }
            if (equipmentType != null) {
                equipmentRegistry.setEquipmentType(equipmentType);
            }
            equipmentDAL.save(equipmentRegistry);
        }
    }
}
