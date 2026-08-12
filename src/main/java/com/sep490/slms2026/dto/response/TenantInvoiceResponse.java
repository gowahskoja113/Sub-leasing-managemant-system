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
    private String cycleType;
    private String propertyName;
    private String roomNumber;
    private Integer month;
    private Integer year;
    private String billingPeriod;
    private List<TenantInvoiceItemResponse> items;
    /**
     * Cách tính tiền (cọc onboard / FIRST pro-rata / REGULAR) — FE UI minh bạch.
     * Ưu tiên field này + lines[]; items[] vẫn giữ cho list gọn.
     */
    private PaymentBreakdownResponse paymentBreakdown;
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
    private Boolean autoIssued;
    /**
     * true nếu tiền nhà chu kỳ đầu đã thu cùng QR onboard ({@code onboardPaid=true} trong note),
     * hoặc đây là hoá đơn {@code HD-ONBOARD-*}. FE gắn nhãn "đã thu lúc nhận nhà".
     */
    private Boolean onboardPaid;
}
