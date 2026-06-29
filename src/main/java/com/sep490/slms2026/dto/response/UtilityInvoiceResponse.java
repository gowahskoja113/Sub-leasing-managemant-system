package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.UtilityInvoiceStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilityInvoiceResponse {
    private Long id;
    private Long propertyId;
    private String propertyName;
    private Long roomId;
    private String roomNumber;
    private String type;
    private String billingPeriod;
    private BigDecimal prevReading;
    private BigDecimal newReading;
    private BigDecimal consumption;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String meterImageUrl;
    private UtilityInvoiceStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private String tenantFullName;
    private String tenantPhone;
}
