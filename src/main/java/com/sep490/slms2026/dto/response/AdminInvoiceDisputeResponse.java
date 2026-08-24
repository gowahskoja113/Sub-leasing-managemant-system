package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminInvoiceDisputeResponse {
    private Long id;
    private String status;
    private String reason;
    private String note;
    private List<String> photos;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolutionNote;
    private Long replacementInvoiceId;
    private String replacementInvoiceCode;

    private Long invoiceId;
    private String invoiceCode;
    private String invoiceType;
    private String invoiceStatus;
    private String billingPeriod;
    private BigDecimal amount;
    private LocalDate dueDate;
    private LocalDateTime paidAt;

    private String propertyName;
    private String propertyAddress;
    private Boolean wholeHouse;
    private String propertyType;
    private String roomNumber;

    private String tenantName;
    private String tenantPhone;
    private String managerName;

    private BigDecimal prevReading;
    private BigDecimal newReading;
    private BigDecimal consumption;
    private BigDecimal unitPrice;
    private String meterImageUrl;
    private LocalDateTime meterCapturedAt;
    private String utilityBillImageUrl;
    private String billingAddress;
    private String customerCode;
    private Boolean refundRequired;
}
