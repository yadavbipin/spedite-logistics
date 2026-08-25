package com.spedite.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceDetails {
    private Boolean insured;
    private String insuranceCompany;
    private String policyNumber;
    private LocalDate insuranceDate;
    private Double insuranceAmount;
    private String notes;
}

