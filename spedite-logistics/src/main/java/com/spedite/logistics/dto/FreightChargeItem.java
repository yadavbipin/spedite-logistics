package com.spedite.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightChargeItem {
    private String chargeType;
    private String description;
    private BigDecimal amount;
    private Boolean taxable;
    private String direction; // BILLABLE or EXPENSE
}
