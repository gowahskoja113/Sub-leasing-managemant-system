package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Thiết bị khách thuê nhận bàn giao theo từng hợp đồng (subset inventory nhà/phòng).
 */
@Entity
@Table(
        name = "tenant_contract_equipments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_contract_id", "equipment_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContractEquipment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_contract_id", nullable = false)
    private TenantContract tenantContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_at_signing")
    private EquipmentStatus conditionAtSigning;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;
}
