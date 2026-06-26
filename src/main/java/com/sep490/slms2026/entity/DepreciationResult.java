package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "depreciation_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_contract_id", nullable = false)
    private InboundContract inboundContract;

    // null = khấu hao cấp nhà nguyên căn; có giá trị = khấu hao riêng từng phòng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    // ===== Các thành phần vốn đầu tư =====
    @Column(name = "total_renovation_cost", nullable = false)
    private BigDecimal totalRenovationCost;     // tổng chi phí sửa chữa/decor

    @Column(name = "total_equipment_cost", nullable = false)
    private BigDecimal totalEquipmentCost;      // tổng thiết bị mua mới (source=PURCHASED)

    @Column(name = "total_rent_amount", nullable = false)
    private BigDecimal totalRentAmount;         // phần tổng tiền thuê HĐ (phân bổ theo phòng nếu chia phòng)

    @Column(name = "total_investment", nullable = false)
    private BigDecimal totalInvestment;         // = totalRentAmount + renovation + equipment

    // ===== Kết quả tính khấu hao =====
    @Column(name = "contract_months", nullable = false)
    private Integer contractMonths;             // số tháng hợp đồng gốc

    @Column(name = "monthly_depreciation", nullable = false)
    private BigDecimal monthlyDepreciation;     // = totalInvestment / contractMonths (giá hoàn vốn/tháng)

    // ===== Gợi ý giá thuê =====
    @Column(name = "suggested_min_price", nullable = false)
    private BigDecimal suggestedMinPrice;       // = monthlyDepreciation (hoàn vốn, chưa lời)

    @Column(name = "suggested_price_with_profit", nullable = false)
    private BigDecimal suggestedPriceWithProfit;

    @Column(name = "room_floor")
    private BigDecimal roomFloor;

    @Column(name = "effective_m2")
    private Double effectiveM2;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}