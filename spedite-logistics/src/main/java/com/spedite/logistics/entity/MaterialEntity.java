package com.spedite.logistics.entity;

import com.spedite.logistics.dto.Dimensions;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "material_details")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long materialId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private BookingEntity booking;

    private String materialName;
    private String packagingType;
    private Integer noOfArticles;
    private BigDecimal actualWeight;
    private BigDecimal chargedWeight;
    @Column(name = "rate", precision = 10, scale = 2)
    private BigDecimal rate;
    private String hsnCode;
    private String containerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Dimensions dimensions;
}
