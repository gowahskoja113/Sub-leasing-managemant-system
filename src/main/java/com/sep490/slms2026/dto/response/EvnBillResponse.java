package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class EvnBillResponse {
    private Long id;
    private Long propertyId;
    private String propertyName;
    private String billingPeriod;
    private Integer month;
    private Integer year;
    private Integer totalKwh;
    private BigDecimal totalAmount;
    private BigDecimal unitPrice;
    private String imageUrl;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
}
