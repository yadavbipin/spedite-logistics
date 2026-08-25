package com.spedite.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightSettlementDetails {
    private BigDecimal brokerAdvancePaid;
    private BigDecimal brokerBalancePaid;
    private BigDecimal consigneeReceivedAmount;
    private LocalDate podReceivedDate;
    private String paymentMode;
    private String referenceNumber;
    private String notes;
}
