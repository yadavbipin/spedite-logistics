package com.spedite.logistics.repository;

import com.spedite.logistics.entity.BookingEntity;
import com.spedite.logistics.entity.MaterialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialRepository extends JpaRepository<MaterialEntity, Long> {
    void deleteByBooking(BookingEntity booking);
    boolean existsByBooking(BookingEntity booking);
    List<MaterialEntity> findByBooking(BookingEntity booking);
}
