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
public class ManagerInvoiceResponse {
    private Long id;
    private String code;
    private String type;
    private Long propertyId;
    private String propertyName;
    private String roomNumber;
    private String tenantName;
    private Integer month;
    private Integer year;
    private String billingPeriod;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private BigDecimal lateFee;
    private String cycleType;
    private String status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private String paymentMethod;
    private String transactionId;
    private String payosQrCode;
    private String payosCheckoutUrl;
    /** Chỉ populate ở GET /manager/invoices/{id} — list để nhẹ payload. */
    private List<TenantInvoiceItemResponse> items;
    /**
     * Cách tính tiền — detail endpoint; list thường null (trừ khi cần).
     * Manager RENT: vẫn có structure/formula text nhưng số tiền đã mask null theo policy.
     */
    private PaymentBreakdownResponse paymentBreakdown;
    /**
     * true nếu đã thu cùng QR onboard (FIRST {@code onboardPaid=true} hoặc hoá đơn HD-ONBOARD).
     */
    private Boolean onboardPaid;
}
