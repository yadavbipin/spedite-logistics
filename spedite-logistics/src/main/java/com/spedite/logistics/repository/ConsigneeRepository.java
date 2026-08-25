package com.spedite.logistics.repository;

import com.spedite.logistics.entity.ConsigneeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsigneeRepository extends JpaRepository<ConsigneeEntity, Long> {
    Optional<ConsigneeEntity> findByGstNumber(String gstNumber);
}

