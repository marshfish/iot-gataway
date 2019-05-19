package com.mcode.gateway.business.dal;

import com.mcode.gateway.business.dal.dao.Configuration;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ConfigurationDAL extends CrudRepository<Configuration, Long>, JpaSpecificationExecutor<Configuration> {
    int removeByTypeAndConfigType(Integer type, Integer configType);

    List<Configuration> getByTypeAndConfigType(Integer type, Integer configType);

    List<Configuration> getByConfigType(Integer configType);
}
