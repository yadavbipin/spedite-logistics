package com.spedite.logistics.entity;

import com.spedite.logistics.dto.*;
import com.spedite.logistics.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "booking")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;
    @Column(name = "lr_number", nullable = false, unique = true)
    private String lrNumber;
    @Column(name = "booking_date", nullable = false)
    private LocalDate lrDate;
    private String ewayBillNo;
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status")
    private BookingStatus bookingStatus;
    private String transportMode;
    private String riskType;

    private LocalDate loadingDate;
    private LocalDate reportingDate;

    private String remarks;

    @ManyToOne
    @JoinColumn(name = "consignor_id")
    private ConsignorEntity consignor;

    @ManyToOne
    @JoinColumn(name = "consignee_id")
    private ConsigneeEntity consignee;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private TruckDetails truckDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<MaterialDto> materialDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private FreightDetails freightDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private InsuranceDetails insuranceDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private DemurrageDetails demurrageDetails;

    @CreationTimestamp
    private LocalDateTime createdAt;
}


