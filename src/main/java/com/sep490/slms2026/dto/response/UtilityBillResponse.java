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

    /** Phần tiêu thụ đã phát hành cho khách (kWh/m³). Null khi nhà chia phòng chưa đọc đủ phòng. */
    private BigDecimal billedToTenantQuantity;
    /** Phần tiêu thụ công ty chịu (hao hụt / trước khi đón khách). */
    private BigDecimal companyBornQuantity;

    /** Tổng tiêu thụ các phòng đã ghi trong kỳ (chia phòng). Null với nguyên căn / chưa có HĐ phòng. */
    private BigDecimal roomSumQuantity;
    /** Trần cho phép = totalQuantity × (1 + tolerance%). */
    private BigDecimal roomSumCap;
    /** Số phòng đã phát hành hoá đơn kỳ này (alias roomsDone cho FE tiến độ). */
    private Integer roomsBilled;
    /** Số phòng cần ghi kỳ này (alias roomsTotal). */
    private Integer roomsExpected;
}

