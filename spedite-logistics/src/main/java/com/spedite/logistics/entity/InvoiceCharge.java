package com.spedite.logistics.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chargeId;

    @JsonBackReference("invoice-charges")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private String chargeType; // FREIGHT, HALTING, TOLL, etc.

    private String description;

    private BigDecimal rate;
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal amount;

    private Boolean taxable;

    private LocalDateTime createdAt;

    // getters & setters
}
