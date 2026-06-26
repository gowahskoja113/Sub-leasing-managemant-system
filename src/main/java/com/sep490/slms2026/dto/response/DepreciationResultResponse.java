package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingScope;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationResultResponse {

    private Long id;
    private Long propertyId;
    private Long inboundContractId;
    private PricingScope pricingScope;
    private Long roomId;
    private String roomNumber;

    /** Phần thuê phân bổ cho phòng. */
    private BigDecimal rentShare;
    /** Phần cải tạo phân bổ cho phòng. */
    private BigDecimal renovationShare;
    /** Phần thiết bị phân bổ cho phòng. */
    private BigDecimal equipmentShare;

    /** @deprecated Dùng rentShare + renovationShare + equipmentShare. Giữ để tương thích. */
    private BigDecimal totalRenovationCost;
    /** @deprecated Dùng equipmentShare. */
    private BigDecimal totalEquipmentCost;
    /** @deprecated Dùng rentShare. */
    private BigDecimal totalRentAmount;

    private BigDecimal totalInvestment;
    private Integer contractMonths;
    private BigDecimal monthlyBreakEven;
    /** Giá sàn: hoàn vốn + OPEX + buffer trống phòng. */
    private BigDecimal roomFloor;
    private BigDecimal suggestedMinPrice;
    /** Giá gợi ý sau lợi nhuận / ROI (Room_Price_i). */
    private BigDecimal suggestedPriceWithProfit;

    private Double area;
    private Double effectiveM2;
    private Double weight;
    /** true nếu suggestedPriceWithProfit < roomFloor */
    private Boolean belowFloor;

    private LocalDateTime calculatedAt;
}
