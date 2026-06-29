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
public class TenantInvoiceResponse {
    private Long id;
    private String code;
    private String type;
    private String propertyName;
    private String roomNumber;
    private Integer month;
    private Integer year;
    private String billingPeriod;
    private List<TenantInvoiceItemResponse> items;
    private BigDecimal totalAmount;
    private BigDecimal lateFee;
    private BigDecimal grandTotal;
    private String status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private String paymentMethod;
    private String transactionId;
    private BigDecimal kwhUsed;
    private BigDecimal electricityRate;
    private BigDecimal m3Used;
    private BigDecimal waterRate;
    private String payosCheckoutUrl;
    private String payosQrCode;
    private Long payosOrderCode;
}
