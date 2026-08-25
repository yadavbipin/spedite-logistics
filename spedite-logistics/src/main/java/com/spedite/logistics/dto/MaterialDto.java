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
public class MaterialDto {
    private String materialName;
    private String packagingType;
    private Integer noOfArticles;
    private BigDecimal actualWeight;
    private BigDecimal chargedWeight;
    private BigDecimal rate;
    private String hsnCode;
    private String containerName;
    private Dimensions dimensions;
}

