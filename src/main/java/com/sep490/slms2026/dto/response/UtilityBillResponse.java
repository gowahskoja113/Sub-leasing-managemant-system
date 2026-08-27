package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class UtilityBillResponse {
    private Long id;
    private Long propertyId;
    private String propertyName;
    /** ELECTRICITY | WATER */
    private String type;
    private String billingPeriod;
    private Integer month;
    private Integer year;
    /** kWh hoặc m³ — field chính cho FE utility-bills. */
    private Integer totalQuantity;
    private BigDecimal totalAmount;
    /** Đơn giá chưa làm tròn (scale 8). */
    private BigDecimal unitPrice;
    /** Cùng giá trị unitPrice — FE dùng khi cần đủ chữ số thập phân. */
    private BigDecimal unitPriceExact;
    private String imageUrl;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;

    /** Số phòng có HĐ ACTIVE cần ghi chỉ số. Nhà nguyên căn = 0. */
    private Integer roomsTotal;
    /** Số phòng đã có UtilityInvoice kỳ này. */
    private Integer roomsDone;
    /** Hạn chụp trong ngày — null với nhà nguyên căn. */
    private LocalDate readingDeadline;
    /** Quá hạn mà chưa ghi đủ phòng. */
    private Boolean overdue;

    /** Phần tiêu thụ đã phát hành cho khách (kWh/m³). Null nếu chưa phát hành nguyên căn. */
    private BigDecimal billedToTenantQuantity;
    /** Phần tiêu thụ công ty chịu khi khách dọn giữa kỳ. */
    private BigDecimal companyBornQuantity;
}

