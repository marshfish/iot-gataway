package com.hc.business.dal;

import com.hc.business.dal.dao.EquipmentRegistry;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EquipmentDAL extends CrudRepository<EquipmentRegistry, Long>, JpaSpecificationExecutor<EquipmentRegistry> {
    int countByUniqueId(String uniqueId);

    int deleteByUniqueId(String uniqueId);

    List<EquipmentRegistry> getByUniqueId(String uniqueId);

    List<EquipmentRegistry> getByEquipmentIdAndEquipmentType(String equipmentId, Integer equipmentType);

}
