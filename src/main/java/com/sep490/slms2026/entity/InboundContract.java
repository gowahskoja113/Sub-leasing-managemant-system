package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "inbound_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundContract implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false, unique = true)
    private Property property;

    @Column(name = "contract_code", unique = true, nullable = false)
    private String contractCode;

    @Column(name = "owner_name", nullable = false)
    private String ownerName; // Tên chủ nhà gốc

    @Column(name = "base_rent_price", nullable = false)
    private BigDecimal baseRentPrice; // Giá thuê gốc/tháng

    @Column(name = "deposit_amount", nullable = false)
    private BigDecimal depositAmount; // Tiền cọc gốc trả cho chủ nhà

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "contract_scan_url")
    private String contractScanUrl; // File scan hợp đồng gốc

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status = ContractStatus.PENDING; // PENDING, ACTIVE, EXPIRED, TERMINATED
}