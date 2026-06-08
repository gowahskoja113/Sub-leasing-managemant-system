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

    @Column(name = "original_deposit", nullable = false)
    private BigDecimal originalDeposit;         // cọc trả cho chủ gốc

    @Column(name = "total_investment", nullable = false)
    private BigDecimal totalInvestment;         // = renovation + equipment + deposit

    // ===== Kết quả tính khấu hao =====
    @Column(name = "contract_months", nullable = false)
    private Integer contractMonths;             // số tháng hợp đồng gốc

    @Column(name = "monthly_depreciation", nullable = false)
    private BigDecimal monthlyDepreciation;     // = totalInvestment / contractMonths

    // ===== Gợi ý giá thuê =====
    @Column(name = "base_rent", nullable = false)
    private BigDecimal baseRent;                // giá thuê gốc/tháng từ InboundContract

    @Column(name = "monthly_operating_cost")
    private BigDecimal monthlyOperatingCost;    // chi phí vận hành (điện chung, internet...)

    @Column(name = "suggested_min_price", nullable = false)
    private BigDecimal suggestedMinPrice;       // = baseRent + depreciation + operatingCost

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}