package com.spedite.logistics.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDto {

    private String ewayBillNo;
    private LrDetails lrDetails;
    private ConsignorDto consignor;
    private ConsigneeDto consignee;

    private TruckDetails truckDetails;
    private List<MaterialDto> materials;

    private FreightDetails freightDetails;
    private InsuranceDetails insuranceDetails;
    private DemurrageDetails demurrageDetails;

    private String riskType;
    private String transportMode;

    private LocalDate loadingDate;
    private LocalDate reportingDate;

    private String remarks;
}

