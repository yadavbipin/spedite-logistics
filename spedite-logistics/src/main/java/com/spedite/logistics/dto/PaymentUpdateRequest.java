package com.spedite.logistics.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentUpdateRequest {
    private BigDecimal receivedAmount;
    private String paymentMode;
    private String referenceNumber;
    private LocalDate podReceivedDate;
    private String notes;
}
