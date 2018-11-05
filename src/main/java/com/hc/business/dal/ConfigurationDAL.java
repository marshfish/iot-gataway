package com.hc.business.dal;

import com.hc.business.dal.dao.Configuration;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ConfigurationDAL extends CrudRepository<Configuration, Long>, JpaSpecificationExecutor<Configuration> {
    int removeByDescKeyAndConfigType(Integer type,Integer configType);

    List<Configuration> getByDescKeyAndConfigType(Integer type,Integer configType);

    List<Configuration> getByConfigType(Integer configType);
}
