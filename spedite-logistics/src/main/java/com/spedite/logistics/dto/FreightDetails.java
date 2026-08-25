package com.spedite.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightDetails {
    private String freightType;
    private Double basicFreight;
    private Object otherCharges;
    private Object gstDetails;
    private Object advanceDetails;
    private Object tdsDetails;
    private Boolean hideFreightInPdf;
    private FreightSettlementDetails settlementDetails;
    private List<FreightChargeItem> manualCharges;
}
