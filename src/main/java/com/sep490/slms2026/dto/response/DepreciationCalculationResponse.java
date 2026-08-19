package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingMode;
import com.sep490.slms2026.enums.PricingScope;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationCalculationResponse {

    private Long propertyId;
    private PricingScope pricingScope;
    private PricingMode mode;

    // ===== Tổng hợp cấp tòa (theo PRICING_FORMULA.md) =====
    private BigDecimal cRent;
    private BigDecimal cRenovation;
    private BigDecimal cEquipment;
    private BigDecimal capex;
    private Integer contractMonths;
    private BigDecimal monthlyRecovery;
    private BigDecimal fixedOpex;
    private BigDecimal revenueMin;
    private BigDecimal revenueTarget;
    private BigDecimal pDesired;
    private BigDecimal roiExpected;
    private BigDecimal oOperation;
    private BigDecimal vRate;
    private Double commonAreaM2;
    private Double totalWeight;
    private Integer roomCount;

    /** Thời hạn HĐ chủ nhà (ngày kết thúc tính trọn ngày). */
    private Integer leaseMonths;
    /** Ngày nhà bắt đầu cho thuê được (sau cải tạo / hôm nay). */
    private java.time.LocalDate rentableFrom;
    /** Số tháng còn lại tới hạn HĐ, trước khi trừ buffer bàn giao. */
    private Integer rentableMonths;
    /** Cửa sổ bàn giao đang áp (0 nếu HĐ ngắn). */
    private Integer handoverBufferMonths;
    /** Mẫu số chia vốn = rentableMonths − handoverBufferMonths. */
    private Integer revenueMonths;

    private DepreciationResultResponse wholeHouseResult;
    private List<DepreciationResultResponse> roomResults;
}
