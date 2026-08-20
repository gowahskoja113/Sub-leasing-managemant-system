package com.sep490.slms2026.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutInspectionRequest {
    private List<String> photos;
    private String roomConditionNote;
    private Integer electricityFinalReading;
    private String electricMeterImageUrl;
    private Integer waterFinalReading;
    private String waterMeterImageUrl;
    private java.math.BigDecimal electricityUnitPrice;
    private java.math.BigDecimal waterUnitPrice;
    private List<CheckoutDamageItemRequest> damages;
}
