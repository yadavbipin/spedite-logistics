package com.spedite.logistics.repository;

import com.spedite.logistics.entity.BookingEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    @EntityGraph(attributePaths = {"consignor", "consignee"})
    Optional<BookingEntity> findByLrNumber(String lrNumber);

    @EntityGraph(attributePaths = {"consignor", "consignee"})
    @Query("select b from BookingEntity b order by b.createdAt desc, b.bookingId desc")
    java.util.List<BookingEntity> findAllByOrderByCreatedAtDescBookingIdDesc();

    boolean existsByLrNumber(String lrNumber);
}
