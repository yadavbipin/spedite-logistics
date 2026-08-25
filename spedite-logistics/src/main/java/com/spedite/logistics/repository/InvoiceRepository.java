package com.spedite.logistics.repository;

import com.spedite.logistics.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByBooking_BookingId(Long invoiceId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    @Query("""
            select distinct i
            from Invoice i
            left join fetch i.booking b
            left join fetch b.consignor
            left join fetch b.consignee
            left join fetch i.charges
            where i.invoiceId = :invoiceId
            """)
    Optional<Invoice> findDetailedByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
            select distinct i
            from Invoice i
            left join fetch i.booking b
            left join fetch b.consignor
            left join fetch b.consignee
            left join fetch i.charges
            where b.bookingId = :bookingId
            """)
    Optional<Invoice> findDetailedByBookingId(@Param("bookingId") Long bookingId);

    @Query("""
            select distinct i
            from Invoice i
            left join fetch i.booking b
            left join fetch b.consignor
            left join fetch b.consignee
            left join fetch i.charges
            order by i.createdAt desc, i.invoiceId desc
            """)
    List<Invoice> findAllDetailed();
}
