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
public class ConsignorDto {
    private Long consignorId;
    private String name;
    private String gstNumber;
    private String contactNumber;
    private List<String> email;
    private Address address;
}

