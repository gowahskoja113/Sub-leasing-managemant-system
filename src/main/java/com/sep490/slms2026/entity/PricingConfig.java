package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.PricingMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pricing_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20, nullable = false)
    private PricingMode mode;

    /** FORWARD: lãi ròng muốn thu mỗi tháng (VND). */
    @Column(name = "p_desired", precision = 19, scale = 2)
    private BigDecimal pDesired;

    /** REVERSE: ROI %/năm. */
    @Column(name = "roi_expected", precision = 19, scale = 4)
    private BigDecimal roiExpected;

    /** Chi phí vận hành khác (không gồm lương quản lý). */
    @Column(name = "o_operation", precision = 19, scale = 2, nullable = false)
    private BigDecimal oOperation;

    /** JSON map userId → lương (chỉ lưu mức > 0). */
    @Column(name = "manager_salaries_json", columnDefinition = "TEXT")
    private String managerSalariesJson;

    /** % tăng giá thuê mỗi 01/01. */
    @Column(name = "annual_increase_pct", precision = 19, scale = 4, nullable = false)
    private BigDecimal annualIncreasePct;

    /** Thuê chưa đủ bấy nhiêu tháng tới 01/01 thì hoãn kỳ tăng. */
    @Column(name = "escalation_grace_months", nullable = false)
    private int escalationGraceMonths;

    /** Từ trước 01/01 bấy nhiêu tháng, quản lý báo giá năm sau. */
    @Column(name = "new_year_price_lead_months", nullable = false)
    private int newYearPriceLeadMonths;

    /** Biên dự phòng trống phòng (%). */
    @Column(name = "v_rate_pct", precision = 19, scale = 4, nullable = false)
    private BigDecimal vRatePct;

    /** Số tháng cuối kỳ không tính doanh thu khi định giá. */
    @Column(name = "handover_buffer_months", nullable = false)
    private int handoverBufferMonths;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static PricingConfig defaults() {
        return PricingConfig.builder()
                .id(SINGLETON_ID)
                .mode(PricingMode.FORWARD)
                .pDesired(new BigDecimal("10000000"))
                .roiExpected(BigDecimal.ZERO)
                .oOperation(new BigDecimal("2000000"))
                .managerSalariesJson("{}")
                .annualIncreasePct(new BigDecimal("5"))
                .escalationGraceMonths(6)
                .newYearPriceLeadMonths(2)
                .vRatePct(new BigDecimal("10"))
                .handoverBufferMonths(1)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
