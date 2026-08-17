package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "utility_bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilityBill implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    /** ELECTRIC | WATER — hoá đơn nhà nước admin chốt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'ELECTRIC'")
    @Builder.Default
    private UtilityType type = UtilityType.ELECTRIC;

    @Column(name = "billing_period", nullable = false)
    private String billingPeriod;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    /** kWh (điện) hoặc m³ (nước). Cột DB giữ tên total_quantity để khỏi rename dữ liệu cũ. */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal unitPrice;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UtilityBillStatus status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Nhà chia phòng: hạn chụp đồng hồ = ngày phát hành hoá đơn tổng. */
    @Column(name = "reading_deadline")
    private LocalDate readingDeadline;
}

