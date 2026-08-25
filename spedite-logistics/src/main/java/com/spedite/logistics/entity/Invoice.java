package com.spedite.logistics.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Data
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invoiceId;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @Column(nullable = false)
    private String billToType; // CONSIGNOR / CONSIGNEE

    @Column(nullable = false)
    private Long billToId;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "PENDING";

    private LocalDate invoiceDate;
    private LocalDate dueDate;

    private BigDecimal subtotalAmount;

    private Boolean gstApplicable;
    private BigDecimal gstPercent;

    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal igst;

    private BigDecimal totalInvoiceAmount;

    private String invoiceStatus;

    private LocalDateTime createdAt;

    @Transient
    private BigDecimal brokerAdvancePaidAmount;

    @Transient
    private BigDecimal brokerBalancePaidAmount;

    @Transient
    private BigDecimal brokerTotalPaidAmount;

    @Transient
    private BigDecimal consigneeReceivedAmount;

    @Transient
    private BigDecimal expenseChargesAmount;

    @Transient
    private BigDecimal billableExtraChargesAmount;

    @Transient
    private BigDecimal amountPendingFromConsignee;

    @Transient
    private BigDecimal netProfitLossAmount;

    @Transient
    private String profitLossBasis;

    @Transient
    private String profitLossStatus;

    @Transient
    private LocalDate podReceivedDate;

    @Transient
    private String settlementSummary;

    @PrePersist
    @PreUpdate
    private void applyDefaultStatuses() {
        if (paymentStatus == null || paymentStatus.isBlank()) {
            paymentStatus = "PENDING";
        }
        if (invoiceStatus == null || invoiceStatus.isBlank()) {
            invoiceStatus = "UNPAID";
        }
    }

    // --- Relations ---
    @OneToMany(
            mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonManagedReference("invoice-charges")
    private List<InvoiceCharge> charges = new ArrayList<>();

    @OneToMany(
            mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("receivedAt DESC, paymentId DESC")
    @JsonManagedReference("invoice-payments")
    private List<InvoicePayment> payments = new ArrayList<>();

    // getters & setters
}
