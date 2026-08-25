package com.spedite.logistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TruckDetails {
    private String truckNumber;
    private String vehicleType;
    private String fromLocation;
    private String toLocation;
    private Double weightGuarantee;
    private String loadType;
    private DriverDetails driver;
}

