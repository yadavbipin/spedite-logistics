package com.spedite.logistics.repository;

import com.spedite.logistics.entity.ConsignorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsignorRepository extends JpaRepository<ConsignorEntity, Long> {
    Optional<ConsignorEntity> findByGstNumber(String gstNumber);
}
